package com.td.worker.infrastructure.fileregistry;

import com.td.worker.config.WorkerProperties;
import com.td.worker.infrastructure.fileregistry.SharedFileRegistry.ObjectRef;
import com.td.worker.infrastructure.fileregistry.SharedFileRegistry.SharedFileRef;
import com.td.worker.infrastructure.minio.WorkerMinioClient;
import com.td.worker.infrastructure.tenant.TenantDataSourceManager;
import com.td.worker.infrastructure.tenant.TenantRecord;
import com.td.worker.infrastructure.tenant.TenantRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dọn object MinIO dùng chung đã hết đơn vị tham chiếu (yêu cầu người dùng 22/10/2026 — xem
 * {@link SharedFileRegistry}). Mỗi lượt (mặc định 02:30 hằng đêm, chỉ 1 instance nhờ lease DB):
 * <ol>
 *   <li><b>Quét dữ liệu cũ 1 lần/tenant</b>: dòng {@code file_metadata} của văn bản đến tạo trước khi có sổ
 *       đăng ký (object {@code vbdi-delivery/}, {@code national-incoming/}) được đánh dấu {@code is_shared=TRUE}
 *       (từ đó td-web chặn xóa như "Chuyển xử lý"); mọi dòng {@code is_shared=TRUE} được ghi tham chiếu.</li>
 *   <li><b>Dọn dẹp</b>: object hết tham chiếu quá {@code grace-period} (7 ngày) được kiểm tra lại
 *       {@code file_metadata} của MỌI tenant — còn dòng dùng chung thì ghi bù tham chiếu, còn dòng chủ sở hữu
 *       thì hoãn thêm 1 {@code grace-period}, không còn dòng nào mới xóa object. Bất kỳ tenant nào không kiểm
 *       tra được → dừng cả lượt, KHÔNG xóa gì (thà chưa thu hồi dung lượng còn hơn xóa nhầm).</li>
 * </ol>
 *
 * <p>Kết nối tenant: dùng lại pool nếu tenant đang có, nếu không mở kết nối ngắn hạn riêng — KHÔNG gọi
 * {@link TenantDataSourceManager#getDataSource} vì duyệt mọi tenant sẽ evict pool đang phục vụ unit.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SharedFileGcJob {

    static final List<String> LEGACY_SHARED_PREFIXES = List.of("vbdi-delivery/", "national-incoming/");
    private static final Duration LEASE = Duration.ofHours(2);
    private static final int PAGE = 1000;

    private final WorkerProperties props;
    private final SharedFileRegistry registry;
    private final TenantRegistry tenants;
    private final TenantDataSourceManager tenantDataSources;
    private final WorkerMinioClient minio;
    private final TaskScheduler taskScheduler;

    /** Quét dữ liệu cũ ngay sau khi khởi động (không chờ tới lượt đêm) — chặn sớm việc xóa object dùng chung cũ. */
    @EventListener(ApplicationReadyEvent.class)
    public void scheduleStartupBackfill() {
        if (enabled()) {
            taskScheduler.schedule(() -> run(false), Instant.now().plusSeconds(60));
        }
    }

    @Scheduled(cron = "#{@workerScheduleIntervals.fileRegistryGcCron()}")
    public void scheduledRun() {
        run(true);
    }

    public void run(boolean collectGarbage) {
        if (!enabled()) {
            return;
        }
        if (!tenants.isControlPlaneEnabled() || tenants.isRefreshFailed() || tenants.all().isEmpty()) {
            log.warn("Bo qua don dep tep dung chung: chua nap duoc danh sach tenant tu control-plane");
            return;
        }
        String owner = props.resolveInstanceId();
        try {
            if (!registry.tryAcquireGcLease(owner, LEASE)) {
                log.info("Bo qua don dep tep dung chung: instance khac dang chay");
                return;
            }
        } catch (RuntimeException e) {
            log.warn("Khong ket noi duoc so dang ky tep dung chung - bo qua don dep: {}", e.getMessage());
            return;
        }
        try {
            backfillPendingTenants();
            if (collectGarbage) {
                collectGarbage();
            }
        } catch (RuntimeException e) {
            log.error("Don dep tep dung chung loi - dung luot nay", e);
        } finally {
            try {
                registry.releaseGcLease(owner);
            } catch (RuntimeException e) {
                log.warn("Khong tra duoc lease don dep tep dung chung (tu het han sau {}): {}", LEASE, e.getMessage());
            }
        }
    }

    private boolean enabled() {
        return registry.isEnabled() && props.getFileRegistry().isGcEnabled();
    }

    void backfillPendingTenants() {
        for (TenantRecord tenant : tenants.all().values()) {
            String tenantId = tenant.tenantId();
            try {
                if (registry.isBackfilled(tenantId)) {
                    continue;
                }
                JdbcTemplate jdbc = new JdbcTemplate(tenantDataSource(tenant));
                int marked = markLegacySharedRows(jdbc);
                int registered = registerSharedRows(tenantId, jdbc);
                registry.markBackfilled(tenantId, marked, registered);
                log.info("Quet du lieu cu tep dung chung tenant {}: danh dau {} dong, ghi {} tham chieu",
                    tenantId, marked, registered);
            } catch (RuntimeException e) {
                log.warn("Quet du lieu cu tep dung chung tenant {} loi (thu lai luot sau): {}", tenantId, e.getMessage());
            }
        }
    }

    private int markLegacySharedRows(JdbcTemplate jdbc) {
        StringBuilder sql = new StringBuilder(
            "UPDATE file_metadata SET is_shared = TRUE WHERE is_shared = FALSE AND document_id IS NOT NULL AND (");
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < LEGACY_SHARED_PREFIXES.size(); i++) {
            sql.append(i == 0 ? "" : " OR ").append("file_path LIKE ?");
            args.add(LEGACY_SHARED_PREFIXES.get(i) + "%");
        }
        return jdbc.update(sql.append(")").toString(), args.toArray());
    }

    private int registerSharedRows(String tenantId, JdbcTemplate jdbc) {
        int total = 0;
        UUID after = null;
        while (true) {
            List<SharedFileRef> page = after == null
                ? jdbc.query("""
                        SELECT id, document_id, file_path, bucket_name FROM file_metadata
                        WHERE is_shared = TRUE AND document_id IS NOT NULL ORDER BY id LIMIT ?
                        """, (rs, i) -> toRef(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4)), PAGE)
                : jdbc.query("""
                        SELECT id, document_id, file_path, bucket_name FROM file_metadata
                        WHERE is_shared = TRUE AND document_id IS NOT NULL AND id > ? ORDER BY id LIMIT ?
                        """, (rs, i) -> toRef(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4)), after, PAGE);
            if (page.isEmpty()) {
                return total;
            }
            registry.register(tenantId, SharedFileRegistry.SOURCE_BACKFILL, page);
            total += page.size();
            after = page.get(page.size() - 1).fileMetadataId();
        }
    }

    void collectGarbage() {
        if (!minio.isEnabled()) {
            log.warn("Bo qua xoa object tep dung chung: MinIO chua bat (MINIO_ENABLED=false)");
            return;
        }
        WorkerProperties.FileRegistry cfg = props.getFileRegistry();
        Instant runStartedAt = Instant.now();
        Instant cutoff = runStartedAt.minus(cfg.getGracePeriod());
        int deleted = 0;
        int kept = 0;
        for (int batch = 0; batch < Math.max(1, cfg.getGcMaxBatchesPerRun()); batch++) {
            List<ObjectRef> candidates = registry.findGcCandidates(cutoff, runStartedAt, Math.max(1, cfg.getGcBatchSize()));
            if (candidates.isEmpty()) {
                break;
            }
            Map<String, List<TenantRow>> usage = findRowsInAllTenants(candidates);
            for (ObjectRef candidate : candidates) {
                List<TenantRow> rows = usage.get(candidate.objectKey());
                if (rows != null && !rows.isEmpty()) {
                    keepReferenced(candidate, rows);
                    kept++;
                } else if (delete(candidate)) {
                    deleted++;
                }
            }
        }
        log.info("Don dep tep dung chung: xoa {} object, giu {} object con dong file_metadata tham chieu", deleted, kept);
    }

    private void keepReferenced(ObjectRef candidate, List<TenantRow> rows) {
        Map<String, List<SharedFileRef>> sharedByTenant = new LinkedHashMap<>();
        for (TenantRow row : rows) {
            if (row.shared()) {
                sharedByTenant.computeIfAbsent(row.tenantId(), k -> new ArrayList<>()).add(row.ref());
            }
        }
        if (sharedByTenant.isEmpty()) {
            // Chi con dong chu so huu (vd van ban goc da "Chuyen xu ly" di) - doi them 1 grace-period.
            registry.postpone(candidate);
            return;
        }
        log.warn("Object {} con {} dong file_metadata dung chung chua co tham chieu - ghi bu", candidate.objectKey(),
            sharedByTenant.values().stream().mapToInt(List::size).sum());
        sharedByTenant.forEach((tenantId, refs) -> registry.register(tenantId, SharedFileRegistry.SOURCE_GC_REPAIR, refs));
        registry.markChecked(candidate);
    }

    private boolean delete(ObjectRef candidate) {
        if (!registry.markDeletedIfUnreferenced(candidate)) {
            registry.markChecked(candidate);
            return false;
        }
        try {
            minio.removeObject(candidate.bucketName(), candidate.objectKey());
            log.info("Da xoa object dung chung het tham chieu {}/{}", candidate.bucketName(), candidate.objectKey());
            return true;
        } catch (Exception e) {
            log.warn("Xoa object {}/{} loi, thu lai luot sau: {}", candidate.bucketName(), candidate.objectKey(), e.getMessage());
            registry.unmarkDeleted(candidate);
            registry.markChecked(candidate);
            return false;
        }
    }

    record TenantRow(String tenantId, boolean shared, SharedFileRef ref) {
    }

    /** objectKey -> dòng {@code file_metadata} (thuộc văn bản) ở mọi tenant. Ném lỗi nếu 1 tenant không kiểm tra được. */
    private Map<String, List<TenantRow>> findRowsInAllTenants(List<ObjectRef> candidates) {
        List<String> paths = candidates.stream().map(ObjectRef::objectKey).distinct().toList();
        Map<String, List<TenantRow>> usage = new HashMap<>();
        for (TenantRecord tenant : tenants.all().values()) {
            String tenantId = tenant.tenantId();
            NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(tenantDataSource(tenant));
            try {
                jdbc.query("""
                        SELECT id, document_id, file_path, bucket_name, is_shared FROM file_metadata
                        WHERE document_id IS NOT NULL AND file_path IN (:paths)
                        """, new MapSqlParameterSource("paths", paths), rs -> {
                        SharedFileRef ref = toRef(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                            rs.getString(3), rs.getString(4));
                        usage.computeIfAbsent(ref.objectKey(), k -> new ArrayList<>())
                            .add(new TenantRow(tenantId, rs.getBoolean(5), ref));
                    });
            } catch (RuntimeException e) {
                throw new IllegalStateException("Khong kiem tra duoc file_metadata tenant " + tenantId
                    + " - khong xoa object nao trong luot nay", e);
            }
        }
        return usage;
    }

    private SharedFileRef toRef(UUID id, UUID documentId, String path, String bucket) {
        return new SharedFileRef(id, documentId, bucket == null || bucket.isBlank() ? minio.bucketName() : bucket, path);
    }

    private DataSource tenantDataSource(TenantRecord tenant) {
        var existing = tenantDataSources.getExisting(tenant.tenantId());
        if (existing.isPresent()) {
            return existing.get();
        }
        String password = tenants.cipher()
            .map(c -> c.decrypt(tenant.encryptedPassword()))
            .orElseThrow(() -> new IllegalStateException("Khong co cipher giai ma mat khau tenant " + tenant.tenantId()));
        DriverManagerDataSource ds = new DriverManagerDataSource(tenant.jdbcUrl(), tenant.username(), password);
        ds.setDriverClassName(tenant.driverClassName() == null || tenant.driverClassName().isBlank()
            ? "org.postgresql.Driver" : tenant.driverClassName());
        return ds;
    }
}
