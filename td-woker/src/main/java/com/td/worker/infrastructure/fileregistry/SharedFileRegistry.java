package com.td.worker.infrastructure.fileregistry;

import com.td.worker.config.WorkerProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Sổ đăng ký tệp đính kèm DÙNG CHUNG (yêu cầu người dùng 22/10/2026): văn bản đến nội tỉnh, văn bản đến từ
 * trục quốc gia và bản sao "Chuyển xử lý" tạo ở MỖI tenant nhận 1 dòng {@code file_metadata} riêng
 * ({@code is_shared=TRUE}) nhưng cùng trỏ 1 object MinIO — không nhân bản dung lượng. Mỗi dòng đó được ghi
 * thành 1 tham chiếu ({@code shared_file_ref}) ở database RIÊNG (không phải control-plane — mỗi tenant 1 DB nên
 * không đếm được bằng {@code file_metadata}). td-web nhả tham chiếu khi văn bản bị xóa vĩnh viễn;
 * {@link SharedFileGcJob} chỉ xóa object khi hết tham chiếu đủ {@code grace-period} VÀ không tenant nào còn
 * dòng {@code file_metadata} trỏ tới.
 *
 * <p>Schema do td-web migrate ({@code db/migration-fileregistry}). Pool KHÔNG đăng ký thành bean
 * {@code DataSource} (Actuator health không bị kéo DOWN khi DB này ngắt), {@code initializationFailTimeout=-1}
 * để không chặn khởi động.</p>
 */
@Slf4j
@Component
public class SharedFileRegistry implements DisposableBean {

    public static final String SOURCE_VBDI_DELIVERY = "vbdi_delivery";
    public static final String SOURCE_NATIONAL_INCOMING = "national_incoming";
    public static final String SOURCE_FORWARD_UNIT = "forward_unit";
    public static final String SOURCE_BACKFILL = "backfill";
    public static final String SOURCE_GC_REPAIR = "gc_repair";

    /** 1 dòng {@code file_metadata} dùng chung ở 1 tenant. */
    public record SharedFileRef(UUID fileMetadataId, UUID documentId, String bucketName, String objectKey) {
    }

    public record ObjectRef(String bucketName, String objectKey) {
    }

    private final WorkerProperties props;
    private volatile HikariDataSource dataSource;

    public SharedFileRegistry(WorkerProperties props) {
        this.props = props;
    }

    public boolean isEnabled() {
        WorkerProperties.FileRegistry cfg = props.getFileRegistry();
        return cfg.isEnabled() && cfg.getUrl() != null && !cfg.getUrl().isBlank();
    }

    /**
     * Gọi SAU KHI transaction tenant đã commit (2 database khác nhau). Lỗi chỉ được log: tham chiếu thiếu
     * không bao giờ dẫn tới xóa nhầm — {@link SharedFileGcJob} luôn kiểm tra lại {@code file_metadata} mọi
     * tenant và tự ghi bù ({@link #SOURCE_GC_REPAIR}).
     */
    public void registerQuietly(String tenantId, String source, List<SharedFileRef> refs) {
        if (!isEnabled() || refs == null || refs.isEmpty()) {
            return;
        }
        try {
            register(tenantId, source, refs);
        } catch (RuntimeException e) {
            log.warn("Khong ghi duoc {} tham chieu tep dung chung (tenant={}, source={}) vao so dang ky: {} "
                + "- job don dep se tu ghi bu", refs.size(), tenantId, source, e.getMessage());
        }
    }

    /** Idempotent theo {@code file_metadata_id} — retry/replay/backfill chạy lại không đếm đôi. */
    public void register(String tenantId, String source, List<SharedFileRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return;
        }
        // Khoa dong shared_file_object theo CUNG 1 thu tu o moi luong ghi -> khong deadlock giua 2 unit
        // cung tham chieu nhieu object.
        List<ObjectRef> objects = refs.stream()
            .map(r -> new ObjectRef(r.bucketName(), r.objectKey()))
            .distinct()
            .sorted(Comparator.comparing(ObjectRef::bucketName).thenComparing(ObjectRef::objectKey))
            .toList();
        JdbcTemplate jdbc = jdbc();
        tx().executeWithoutResult(status -> {
            jdbc.batchUpdate("""
                    INSERT INTO shared_file_object (bucket_name, object_key) VALUES (?, ?)
                    ON CONFLICT (bucket_name, object_key) DO UPDATE SET zero_ref_since = NULL, deleted_at = NULL
                    """, objects, objects.size(), (ps, o) -> {
                    ps.setString(1, o.bucketName());
                    ps.setString(2, o.objectKey());
                });
            jdbc.batchUpdate("""
                    INSERT INTO shared_file_ref (file_metadata_id, tenant_id, document_id, bucket_name, object_key, source)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (file_metadata_id) DO NOTHING
                    """, refs, refs.size(), (ps, r) -> {
                    ps.setObject(1, r.fileMetadataId());
                    ps.setString(2, tenantId);
                    ps.setObject(3, r.documentId());
                    ps.setString(4, r.bucketName());
                    ps.setString(5, r.objectKey());
                    ps.setString(6, source);
                });
        });
    }

    public boolean isBackfilled(String tenantId) {
        Integer n = jdbc().queryForObject(
            "SELECT COUNT(*) FROM shared_file_backfill WHERE tenant_id = ?", Integer.class, tenantId);
        return n != null && n > 0;
    }

    public void markBackfilled(String tenantId, int markedRows, int registeredRows) {
        jdbc().update("""
                INSERT INTO shared_file_backfill (tenant_id, marked_rows, registered_rows) VALUES (?, ?, ?)
                ON CONFLICT (tenant_id) DO NOTHING
                """, tenantId, markedRows, registeredRows);
    }

    /** Lease DB — nhiều instance td-woker chỉ 1 instance chạy dọn dẹp. */
    public boolean tryAcquireGcLease(String owner, Duration leaseFor) {
        return jdbc().update("""
                UPDATE shared_file_gc_lease SET owner = ?, lease_until = now() + (? * interval '1 second')
                WHERE id = 1 AND (lease_until IS NULL OR lease_until < now() OR owner = ?)
                """, owner, leaseFor.toSeconds(), owner) == 1;
    }

    public void releaseGcLease(String owner) {
        jdbc().update("UPDATE shared_file_gc_lease SET lease_until = NULL WHERE id = 1 AND owner = ?", owner);
    }

    /**
     * Object đã hết tham chiếu từ trước {@code cutoff}, chưa xóa, chưa được xét trong lượt chạy này
     * ({@code last_checked_at < runStartedAt}).
     */
    public List<ObjectRef> findGcCandidates(Instant cutoff, Instant runStartedAt, int limit) {
        Timestamp cutoffTs = Timestamp.from(cutoff);
        return jdbc().query("""
                SELECT o.bucket_name, o.object_key FROM shared_file_object o
                WHERE o.deleted_at IS NULL
                  AND o.zero_ref_since IS NOT NULL AND o.zero_ref_since < ?
                  AND (o.last_checked_at IS NULL OR o.last_checked_at < ?)
                  AND NOT EXISTS (SELECT 1 FROM shared_file_ref r
                                  WHERE r.bucket_name = o.bucket_name AND r.object_key = o.object_key
                                    AND (r.released_at IS NULL OR r.released_at > ?))
                ORDER BY o.zero_ref_since
                LIMIT ?
                """, (rs, i) -> new ObjectRef(rs.getString(1), rs.getString(2)),
            cutoffTs, Timestamp.from(runStartedAt), cutoffTs, limit);
    }

    /**
     * Object vẫn còn dòng {@code file_metadata} chủ sở hữu (vd tệp gốc của văn bản đã "Chuyển xử lý") — chờ
     * thêm 1 {@code grace-period} rồi xét lại.
     */
    public void postpone(ObjectRef object) {
        jdbc().update("""
                UPDATE shared_file_object SET zero_ref_since = now(), last_checked_at = now()
                WHERE bucket_name = ? AND object_key = ? AND zero_ref_since IS NOT NULL
                """, object.bucketName(), object.objectKey());
    }

    public void markChecked(ObjectRef object) {
        jdbc().update("UPDATE shared_file_object SET last_checked_at = now() WHERE bucket_name = ? AND object_key = ?",
            object.bucketName(), object.objectKey());
    }

    /** Chốt "đã xóa" TRƯỚC khi gọi MinIO — chỉ thành công khi vẫn không còn tham chiếu nào đang hoạt động. */
    public boolean markDeletedIfUnreferenced(ObjectRef object) {
        return jdbc().update("""
                UPDATE shared_file_object o SET deleted_at = now(), last_checked_at = now()
                WHERE o.bucket_name = ? AND o.object_key = ? AND o.deleted_at IS NULL
                  AND NOT EXISTS (SELECT 1 FROM shared_file_ref r
                                  WHERE r.bucket_name = o.bucket_name AND r.object_key = o.object_key
                                    AND r.released_at IS NULL)
                """, object.bucketName(), object.objectKey()) == 1;
    }

    /** MinIO xóa lỗi — trả về trạng thái chưa xóa để lượt sau thử lại. */
    public void unmarkDeleted(ObjectRef object) {
        jdbc().update("UPDATE shared_file_object SET deleted_at = NULL WHERE bucket_name = ? AND object_key = ?",
            object.bucketName(), object.objectKey());
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource());
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource()));
    }

    private HikariDataSource dataSource() {
        HikariDataSource ds = dataSource;
        if (ds == null) {
            synchronized (this) {
                ds = dataSource;
                if (ds == null) {
                    dataSource = ds = buildPool();
                }
            }
        }
        return ds;
    }

    private HikariDataSource buildPool() {
        WorkerProperties.FileRegistry cfg = props.getFileRegistry();
        HikariConfig config = new HikariConfig();
        config.setPoolName("hikari-file-registry");
        config.setJdbcUrl(cfg.getUrl());
        config.setUsername(cfg.getUsername());
        config.setPassword(cfg.getPassword());
        config.setDriverClassName(cfg.getDriverClassName());
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(Math.max(1, cfg.getMaximumPoolSize()));
        config.setConnectionTimeout(5000);
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }

    @Override
    public void destroy() {
        HikariDataSource ds = dataSource;
        if (ds != null) {
            ds.close();
        }
    }
}
