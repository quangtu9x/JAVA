package com.td.infrastructure.tonghop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.tonghop.ProvinceDispatchSearchRequest;
import com.td.application.tonghop.ProvinceDocumentSearchRequest;
import com.td.application.tonghop.ProvinceDocumentStates;
import com.td.application.tonghop.ProvinceQueryRepository;
import com.td.application.tonghop.ProvinceSyncService;
import com.td.infrastructure.tenant.TenantActiveRegistry;
import com.td.infrastructure.tenant.TenantContext;
import com.td.infrastructure.tenant.TenantRoutingDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Đồng bộ kho tổng hợp toàn tỉnh: với MỖI tenant đang hoạt động, đọc vbden/vbdi/nhật ký gửi nhận thay đổi từ
 * watermark lần trước, chiếu sang {@code province_*} (control-plane).
 *
 * <ul>
 *   <li><b>Tăng dần</b>: thay đổi = {@code GREATEST(created_on, last_modified_on, deleted_on)} &gt; watermark −
 *   {@link #OVERLAP} (chồng lấn để không lọt bản ghi commit trễ). Nhật ký gửi nhận append-only → theo
 *   {@code created_on}; văn bản đi nào có sự kiện mới thì TÍNH LẠI toàn bộ nơi nhận của nó.</li>
 *   <li><b>Toàn bộ</b> (cron đêm/nút "Đồng bộ lại toàn bộ"): quét hết, dòng không còn thấy → {@code deleted}. Cần
 *   vì worker (td-woker) cập nhật một số field bằng JDBC thuần không chạm {@code last_modified_on}.</li>
 *   <li>Chỉ 1 tiến trình chạy tại 1 thời điểm kể cả nhiều instance td-web: {@code pg_try_advisory_lock} trên
 *   control-plane (không có ShedLock trong dự án).</li>
 * </ul>
 * Lỗi 1 tenant được ghi vào {@code province_sync_state.last_error}, không chặn tenant khác.
 */
@Service
@Slf4j
public class ProvinceIndexSyncService implements ProvinceSyncService {

    private static final long ADVISORY_LOCK_KEY = 7_405_101_202_610L;
    private static final Duration OVERLAP = Duration.ofMinutes(10);
    private static final int BATCH = 500;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TenantRoutingDataSource tenantRoutingDataSource;
    private final TenantActiveRegistry tenantActiveRegistry;
    private final DataSource controlPlaneDataSource;
    private final ObjectProvider<DataSource> identityDataSource;
    private final ObjectProvider<ProvinceQueryRepository> queryRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate controlPlane;
    private final TransactionTemplate controlPlaneTx;
    private final JdbcTemplate tenantJdbc;
    private final TransactionTemplate tenantReadTx;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ProvinceIndexSyncService(TenantRoutingDataSource tenantRoutingDataSource,
                                    TenantActiveRegistry tenantActiveRegistry,
                                    @Qualifier("controlPlaneDataSource") DataSource controlPlaneDataSource,
                                    @Qualifier("identityDataSource") ObjectProvider<DataSource> identityDataSource,
                                    ObjectProvider<ProvinceQueryRepository> queryRepository,
                                    ObjectMapper objectMapper) {
        this.queryRepository = queryRepository;
        this.tenantRoutingDataSource = tenantRoutingDataSource;
        this.tenantActiveRegistry = tenantActiveRegistry;
        this.controlPlaneDataSource = controlPlaneDataSource;
        this.identityDataSource = identityDataSource;
        this.objectMapper = objectMapper;
        this.controlPlane = new JdbcTemplate(controlPlaneDataSource);
        this.controlPlaneTx = new TransactionTemplate(new DataSourceTransactionManager(controlPlaneDataSource));
        this.tenantJdbc = new JdbcTemplate(tenantRoutingDataSource);
        this.tenantJdbc.setFetchSize(BATCH);
        TransactionTemplate readTx = new TransactionTemplate(new DataSourceTransactionManager(tenantRoutingDataSource));
        readTx.setReadOnly(true);
        this.tenantReadTx = readTx;
    }

    @Override
    public SyncStatus syncNow(boolean full) {
        run(full);
        return status();
    }

    /** Chạy 1 lượt đồng bộ (bỏ qua nếu đang chạy ở đây/instance khác). Trả true nếu dữ liệu kho có thay đổi. */
    public boolean run(boolean full) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        boolean anyChanged = false;
        try (Connection lockConnection = controlPlaneDataSource.getConnection()) {
            lockConnection.setAutoCommit(true);
            if (!tryAdvisoryLock(lockConnection)) {
                log.info("Dong bo tong hop toan tinh dang chay o instance khac - bo qua lan nay");
                return false;
            }
            try {
                Map<String, String> orgNames = loadOrganizationNames();
                for (String tenantId : tenantRoutingDataSource.knownTenantIds()) {
                    if (tenantActiveRegistry.isActive(tenantId)) {
                        anyChanged |= syncTenantSafely(tenantId, full, orgNames);
                    }
                }
            } finally {
                advisoryUnlock(lockConnection);
            }
        } catch (SQLException ex) {
            log.error("Khong lay duoc ket noi control-plane de dong bo tong hop toan tinh", ex);
        } finally {
            running.set(false);
        }
        if (anyChanged) {
            warmDefaultViews();
        }
        return anyChanged;
    }

    /**
     * Tính sẵn kết quả cho các màn MẶC ĐỊNH (đúng tham số FE gửi khi mở màn) ngay sau khi dữ liệu đổi — người dùng
     * mở màn luôn trúng bộ nhớ đệm thay vì người đầu tiên sau mỗi lần đồng bộ phải chờ truy vấn tổng hợp.
     * Khoá đệm dựng từ {@code toString()} của request, nên request ở đây phải trùng giá trị FE gửi
     * ({@code tonghop/index.jsx}: năm nay; {@code GiamSatVanBanDi.jsx}: 30 ngày, ngưỡng 60 phút/24 giờ;
     * {@code TimKiemToanTinh.jsx}: không lọc, trang 1/20).
     */
    private void warmDefaultViews() {
        ProvinceQueryRepository repository = queryRepository.getIfAvailable();
        if (repository == null) {
            return;
        }
        try {
            LocalDate today = LocalDate.now(ZONE);
            repository.statistics(today.withDayOfYear(1), today);
            ProvinceDispatchSearchRequest dispatch = new ProvinceDispatchSearchRequest();
            dispatch.setFromDate(today.minusDays(29).toString());
            dispatch.setToDate(today.toString());
            dispatch.setLateArrivalMinutes(60);
            dispatch.setLateAcceptHours(24);
            repository.dispatchSummary(dispatch, today.minusDays(29), today, 60, 24);
            repository.searchDispatchDocuments(dispatch, today.minusDays(29), today, 60, 24, 0, 20);
            repository.searchDocuments(new ProvinceDocumentSearchRequest(), null, null, 0, 20);
        } catch (RuntimeException ex) {
            log.warn("Tinh san bo nho dem tong hop toan tinh that bai (khong anh huong dong bo)", ex);
        }
    }

    @Override
    public SyncStatus status() {
        List<TenantSyncStatus> tenants = controlPlane.query("""
                SELECT s.tenant_id, s.last_run_on, s.last_success_on, s.last_full_sync_on, s.last_error,
                       s.last_duration_ms, s.last_changed_rows,
                       (SELECT COUNT(*) FROM province_documents d WHERE d.tenant_id = s.tenant_id AND d.deleted = FALSE) AS docs
                FROM province_sync_state s ORDER BY s.tenant_id
                """, (rs, i) -> TenantSyncStatus.builder()
            .tenantId(rs.getString("tenant_id"))
            .lastRunOn(rs.getObject("last_run_on", OffsetDateTime.class))
            .lastSuccessOn(rs.getObject("last_success_on", OffsetDateTime.class))
            .lastFullSyncOn(rs.getObject("last_full_sync_on", OffsetDateTime.class))
            .lastError(rs.getString("last_error"))
            .lastDurationMs((Long) rs.getObject("last_duration_ms"))
            .lastChangedRows((Integer) rs.getObject("last_changed_rows"))
            .documents(rs.getLong("docs"))
            .build());
        return SyncStatus.builder().running(running.get()).tenants(tenants).build();
    }

    private boolean syncTenantSafely(String tenantId, boolean full, Map<String, String> orgNames) {
        long started = System.currentTimeMillis();
        OffsetDateTime runStart = controlPlane.queryForObject("SELECT NOW()", OffsetDateTime.class);
        try {
            TenantSyncResult result = syncTenant(tenantId, full, orgNames);
            int changed = result.changedRows();
            cpUpdate("""
                    UPDATE province_sync_state SET last_run_on = ?, last_success_on = ?, last_error = NULL,
                           last_duration_ms = ?, last_changed_rows = ?,
                           last_full_sync_on = CASE WHEN ? THEN ? ELSE last_full_sync_on END
                    WHERE tenant_id = ?
                    """, runStart, runStart, System.currentTimeMillis() - started, changed, full, runStart, tenantId);
            return result.dataChanged();
        } catch (RuntimeException ex) {
            log.error("Dong bo tong hop toan tinh that bai cho tenant '{}'", tenantId, ex);
            ensureState(tenantId);
            cpUpdate("""
                    UPDATE province_sync_state SET last_run_on = ?, last_error = ?, last_duration_ms = ? WHERE tenant_id = ?
                    """, runStart, ProvinceText.truncate(String.valueOf(ex.getMessage()), 2000),
                System.currentTimeMillis() - started, tenantId);
            return false;
        }
    }

    private record TenantSyncResult(int changedRows, boolean dataChanged) {
    }

    private TenantSyncResult syncTenant(String tenantId, boolean requestedFull, Map<String, String> orgNames) {
        ensureState(tenantId);
        Map<String, Object> state = controlPlane.queryForMap(
            "SELECT docs_watermark, logs_watermark FROM province_sync_state WHERE tenant_id = ?", tenantId);
        OffsetDateTime docsWatermark = (OffsetDateTime) toOffset(state.get("docs_watermark"));
        OffsetDateTime logsWatermark = (OffsetDateTime) toOffset(state.get("logs_watermark"));
        boolean full = requestedFull || docsWatermark == null;
        Timestamp docsSince = full ? null : Timestamp.from(docsWatermark.minus(OVERLAP).toInstant());
        // Nhật ký gửi nhận append-only: kể cả lần "toàn bộ" vẫn đọc tăng dần (chỉ quét hết khi chưa có watermark).
        Timestamp logsSince = logsWatermark == null ? null : Timestamp.from(logsWatermark.minus(OVERLAP).toInstant());
        LocalDate today = LocalDate.now(ZONE);

        if (full) {
            cpUpdate("DELETE FROM province_sync_seen WHERE tenant_id = ?", tenantId);
        }

        // 1) Văn bản đến + văn bản đi.
        List<ProvinceDocumentRow> rows = new ArrayList<>();
        OffsetDateTime[] maxChanged = {docsWatermark};
        Set<UUID> touchedVbdi = new LinkedHashSet<>();
        int[] changed = {0};
        readTenant(tenantId, () -> {
            tenantJdbc.query(changedDocumentsSql("documents", "vbden", ProvinceDocumentProjector.VBDEN_KEYS, docsSince),
                rs -> {
                    ProvinceDocumentRow row = ProvinceDocumentProjector.vbden(tenantId, rs.getObject("id", UUID.class),
                        rs.getString("title"), rs.getString("status"), readFields(rs),
                        rs.getObject("created_on", OffsetDateTime.class), rs.getObject("last_modified_on", OffsetDateTime.class),
                        rs.getObject("deleted_on") != null, orgNames, today);
                    maxChanged[0] = max(maxChanged[0], rs.getObject("changed_on", OffsetDateTime.class));
                    rows.add(row);
                    if (rows.size() >= BATCH) {
                        changed[0] += upsertDocuments(rows, full);
                        rows.clear();
                    }
                }, docSinceArgs(docsSince));
            tenantJdbc.query(changedDocumentsSql("vbdi_documents", "vbdi", ProvinceDocumentProjector.VBDI_KEYS, docsSince),
                rs -> {
                    ProvinceDocumentRow row = ProvinceDocumentProjector.vbdi(tenantId, rs.getObject("id", UUID.class),
                        rs.getString("title"), rs.getString("status"), readFields(rs),
                        rs.getObject("created_on", OffsetDateTime.class), rs.getObject("last_modified_on", OffsetDateTime.class),
                        rs.getObject("deleted_on") != null, orgNames);
                    maxChanged[0] = max(maxChanged[0], rs.getObject("changed_on", OffsetDateTime.class));
                    touchedVbdi.add(row.getDocumentId());
                    rows.add(row);
                    if (rows.size() >= BATCH) {
                        changed[0] += upsertDocuments(rows, full);
                        rows.clear();
                    }
                }, docSinceArgs(docsSince));
            return null;
        });
        changed[0] += upsertDocuments(rows, full);
        if (full) {
            // Dòng không còn ở nguồn (xoá cứng/đổi tenant) → đánh dấu xoá.
            changed[0] += cpUpdate("""
                    UPDATE province_documents d SET deleted = TRUE, synced_on = NOW()
                    WHERE d.tenant_id = ? AND d.deleted = FALSE
                      AND NOT EXISTS (SELECT 1 FROM province_sync_seen s
                                      WHERE s.tenant_id = d.tenant_id AND s.document_id = d.document_id)
                    """, tenantId);
            cpUpdate("DELETE FROM province_sync_seen WHERE tenant_id = ?", tenantId);
        }

        // 2) Nhật ký gửi nhận → trạng thái từng nơi nhận (chỉ văn bản đi có sự kiện mới).
        OffsetDateTime[] maxLog = {logsWatermark};
        Set<UUID> logVbdi = new LinkedHashSet<>();
        readTenant(tenantId, () -> {
            String sql = "SELECT parent_id, MAX(created_on) AS last_on FROM vbdi_send_log_documents WHERE parent_id IS NOT NULL"
                + (logsSince == null ? "" : " AND created_on > ?") + " GROUP BY parent_id";
            tenantJdbc.query(sql, rs -> {
                logVbdi.add(rs.getObject("parent_id", UUID.class));
                maxLog[0] = max(maxLog[0], rs.getObject("last_on", OffsetDateTime.class));
            }, sinceArgs(logsSince));
            return null;
        });
        rebuildDispatches(tenantId, logVbdi);
        boolean newLogs = maxLog[0] != null && (logsWatermark == null || maxLog[0].isAfter(logsWatermark));

        // 3) Kênh của văn bản đi suy từ nơi nhận thật.
        touchedVbdi.addAll(logVbdi);
        // Tự chữa lệch: văn bản có nơi nhận nhưng cột cuộn sẵn còn 0 (vd thêm cột mới sau lần đồng bộ toàn bộ —
        // từng xảy ra thật với V11, màn Giám sát thiếu gần hết văn bản cho tới lần đồng bộ toàn bộ kế tiếp).
        touchedVbdi.addAll(controlPlane.queryForList("""
                SELECT d.document_id FROM province_documents d
                WHERE d.tenant_id = ? AND d.doc_kind = 'VBDI' AND d.rx_total = 0
                  AND EXISTS (SELECT 1 FROM province_vbdi_dispatches x
                              WHERE x.tenant_id = d.tenant_id AND x.vbdi_id = d.document_id)
                """, UUID.class, tenantId));
        changed[0] += refreshVbdiChannels(tenantId, full ? null : touchedVbdi);

        boolean dataChanged = changed[0] > 0 || newLogs;
        cpUpdate("""
                UPDATE province_sync_state SET docs_watermark = ?, logs_watermark = ?,
                       last_data_change_on = CASE WHEN ? THEN NOW() ELSE last_data_change_on END
                WHERE tenant_id = ?
                """, maxChanged[0], maxLog[0], dataChanged, tenantId);
        return new TenantSyncResult(changed[0], dataChanged);
    }

    private int rebuildDispatches(String tenantId, Collection<UUID> vbdiIds) {
        if (vbdiIds.isEmpty()) {
            return 0;
        }
        int total = 0;
        List<UUID> ids = new ArrayList<>(vbdiIds);
        for (int from = 0; from < ids.size(); from += BATCH) {
            List<UUID> chunk = ids.subList(from, Math.min(ids.size(), from + BATCH));
            UUID[] array = chunk.toArray(UUID[]::new);
            List<ProvinceDispatchAggregator.LogEntry> entries = new ArrayList<>();
            Map<UUID, String> senders = new HashMap<>();
            readTenant(tenantId, () -> {
                tenantJdbc.query(con -> {
                    PreparedStatement ps = con.prepareStatement("""
                            SELECT id, parent_id, send_type, recipient_group, recipient_identifier, recipient_name,
                                   status, content, user_creator, created_on
                            FROM vbdi_send_log_documents
                            WHERE parent_id = ANY(?) AND deleted_on IS NULL
                            """);
                    ps.setArray(1, con.createArrayOf("uuid", array));
                    return ps;
                }, rs -> {
                    ProvinceDispatchAggregator.LogEntry e = new ProvinceDispatchAggregator.LogEntry();
                    e.setId(rs.getObject("id", UUID.class));
                    e.setVbdiId(rs.getObject("parent_id", UUID.class));
                    e.setSendType(rs.getString("send_type"));
                    e.setRecipientGroup(rs.getString("recipient_group"));
                    e.setRecipientIdentifier(rs.getString("recipient_identifier"));
                    e.setRecipientName(rs.getString("recipient_name"));
                    e.setStatus(rs.getString("status"));
                    e.setContent(rs.getString("content"));
                    e.setUserCreator(rs.getString("user_creator"));
                    e.setCreatedOn(rs.getObject("created_on", OffsetDateTime.class));
                    entries.add(e);
                });
                tenantJdbc.query(con -> {
                    PreparedStatement ps = con.prepareStatement(
                        "SELECT id, dynamic_fields->>'unit_publish_identifier' AS sender FROM vbdi_documents WHERE id = ANY(?)");
                    ps.setArray(1, con.createArrayOf("uuid", array));
                    return ps;
                }, rs -> {
                    senders.put(rs.getObject("id", UUID.class), rs.getString("sender"));
                });
                return null;
            });
            List<ProvinceDispatchAggregator.Dispatch> dispatches = ProvinceDispatchAggregator.aggregate(entries);
            total += dispatches.size();
            controlPlaneTx.executeWithoutResult(tx -> {
                controlPlane.update(con -> {
                    PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM province_vbdi_dispatches WHERE tenant_id = ? AND vbdi_id = ANY(?)");
                    ps.setString(1, tenantId);
                    ps.setArray(2, con.createArrayOf("uuid", array));
                    return ps;
                });
                controlPlane.update(con -> {
                    PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM province_vbdi_dispatch_events WHERE tenant_id = ? AND vbdi_id = ANY(?)");
                    ps.setString(1, tenantId);
                    ps.setArray(2, con.createArrayOf("uuid", array));
                    return ps;
                });
                controlPlane.batchUpdate("""
                        INSERT INTO province_vbdi_dispatches
                          (tenant_id, vbdi_id, recipient_key, recipient_group, recipient_identifier, recipient_name,
                           sender_identifier, state, state_label, send_count, first_sent_on, last_sent_on, arrived_on,
                           accepted_on, completed_on, last_event_on, synced_on)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                        """, dispatches, BATCH, (ps, d) -> {
                    ps.setString(1, tenantId);
                    ps.setObject(2, d.getVbdiId());
                    ps.setString(3, d.getRecipientKey());
                    ps.setString(4, d.getRecipientGroup());
                    ps.setString(5, ProvinceText.truncate(d.getRecipientIdentifier(), 200));
                    ps.setString(6, ProvinceText.truncate(d.getRecipientName(), 500));
                    ps.setString(7, ProvinceText.truncate(senders.get(d.getVbdiId()), 200));
                    ps.setString(8, d.getState());
                    ps.setString(9, d.stateLabel());
                    ps.setInt(10, d.getSendCount());
                    ps.setObject(11, d.getFirstSentOn());
                    ps.setObject(12, d.getLastSentOn());
                    ps.setObject(13, d.getArrivedOn());
                    ps.setObject(14, d.getAcceptedOn());
                    ps.setObject(15, d.getCompletedOn());
                    ps.setObject(16, d.getLastEventOn());
                });
                List<ProvinceDispatchAggregator.LogEntry> monitored = entries.stream()
                    .filter(ProvinceDispatchAggregator::isMonitored).toList();
                controlPlane.batchUpdate("""
                        INSERT INTO province_vbdi_dispatch_events
                          (tenant_id, log_id, vbdi_id, recipient_key, send_type, status, content, user_creator, created_on)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (tenant_id, log_id) DO NOTHING
                        """, monitored, BATCH, (ps, e) -> {
                    ps.setString(1, tenantId);
                    ps.setObject(2, e.getId());
                    ps.setObject(3, e.getVbdiId());
                    ps.setString(4, ProvinceDispatchAggregator.recipientKey(e.getRecipientGroup(),
                        e.getRecipientIdentifier(), e.getRecipientName()));
                    ps.setString(5, ProvinceText.truncate(e.getSendType(), 50));
                    ps.setString(6, ProvinceText.truncate(e.getStatus(), 200));
                    ps.setString(7, e.getContent());
                    ps.setString(8, ProvinceText.truncate(e.getUserCreator(), 300));
                    ps.setObject(9, e.getCreatedOn());
                });
            });
        }
        return total;
    }

    /**
     * Cờ kênh + số liệu nơi nhận CUỘN SẴN (cột {@code rx_*}, V11) của văn bản đi, suy từ nơi nhận thật; chỉ ghi
     * dòng có giá trị đổi. Màn giám sát đọc các cột này thay vì nối toàn bộ bảng nơi nhận. Trả số dòng đã đổi.
     */
    private int refreshVbdiChannels(String tenantId, Collection<UUID> vbdiIds) {
        if (vbdiIds != null && vbdiIds.isEmpty()) {
            return 0;
        }
        String sql = """
            UPDATE province_documents d SET is_internal = c.i, is_network = c.n, is_national = c.q,
              rx_total = c.total, rx_waiting = c.waiting, rx_arrived = c.arrived, rx_processing = c.processing,
              rx_done = c.done, rx_refused = c.refused, rx_revoked = c.revoked,
              rx_oldest_waiting_on = c.oldest_waiting, rx_oldest_pending_on = c.oldest_pending,
              rx_first_sent_on = c.first_sent, rx_last_event_on = c.last_event,
              rx_accept_hours_sum = c.accept_sum, rx_accept_count = c.accept_n
            FROM (
              SELECT p.tenant_id, p.document_id,
                COALESCE(BOOL_OR(x.recipient_group = 'internal_unit'), FALSE) AS i,
                COALESCE(BOOL_OR(x.recipient_group = 'contact_unit'), FALSE) AS n,
                COALESCE(BOOL_OR(x.recipient_group = 'external_unit'), FALSE) AS q,
                COUNT(x.vbdi_id)::int AS total,
                COUNT(*) FILTER (WHERE x.state = 'SENT')::int AS waiting,
                COUNT(*) FILTER (WHERE x.state = 'ARRIVED')::int AS arrived,
                COUNT(*) FILTER (WHERE x.state IN ('ACCEPTED', 'ASSIGNED', 'PROCESSING'))::int AS processing,
                COUNT(*) FILTER (WHERE x.state = 'DONE')::int AS done,
                COUNT(*) FILTER (WHERE x.state = 'REFUSED')::int AS refused,
                COUNT(*) FILTER (WHERE x.state = 'REVOKED')::int AS revoked,
                MIN(x.last_sent_on) FILTER (WHERE x.state = 'SENT') AS oldest_waiting,
                MIN(x.last_sent_on) FILTER (WHERE x.state IN ('SENT', 'ARRIVED')) AS oldest_pending,
                MIN(x.first_sent_on) AS first_sent,
                MAX(x.last_event_on) AS last_event,
                COALESCE(SUM(EXTRACT(EPOCH FROM (x.accepted_on - x.last_sent_on))::double precision / 3600.0)
                  FILTER (WHERE x.accepted_on >= x.last_sent_on), 0) AS accept_sum,
                COUNT(*) FILTER (WHERE x.accepted_on >= x.last_sent_on)::int AS accept_n
              FROM province_documents p
              LEFT JOIN province_vbdi_dispatches x ON x.tenant_id = p.tenant_id AND x.vbdi_id = p.document_id
              WHERE p.tenant_id = ? AND p.doc_kind = 'VBDI' %s
              GROUP BY p.tenant_id, p.document_id
            ) c
            WHERE d.tenant_id = c.tenant_id AND d.document_id = c.document_id
              AND (d.is_internal, d.is_network, d.is_national, d.rx_total, d.rx_waiting, d.rx_arrived, d.rx_processing,
                   d.rx_done, d.rx_refused, d.rx_revoked, d.rx_oldest_waiting_on, d.rx_oldest_pending_on,
                   d.rx_first_sent_on, d.rx_last_event_on, d.rx_accept_hours_sum, d.rx_accept_count)
                IS DISTINCT FROM
                  (c.i, c.n, c.q, c.total, c.waiting, c.arrived, c.processing, c.done, c.refused, c.revoked,
                   c.oldest_waiting, c.oldest_pending, c.first_sent, c.last_event, c.accept_sum, c.accept_n)
            """;
        if (vbdiIds == null) {
            return cpUpdate(sql.formatted(""), tenantId);
        }
        UUID[] array = vbdiIds.toArray(UUID[]::new);
        Integer count = controlPlaneTx.execute(tx -> controlPlane.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql.formatted("AND p.document_id = ANY(?)"));
            ps.setString(1, tenantId);
            ps.setArray(2, con.createArrayOf("uuid", array));
            return ps;
        }));
        return count == null ? 0 : count;
    }

    /**
     * Upsert theo lô. Dòng KHÔNG đổi nội dung thì bỏ qua ({@code IS DISTINCT FROM}) — đồng bộ toàn bộ hằng đêm
     * không ghi lại hàng triệu dòng y nguyên (WAL/phình bảng). {@code trackSeen}: ghi id vào
     * {@code province_sync_seen} để bước quét xoá biết dòng nào còn ở nguồn. Trả số dòng thực sự thay đổi.
     */
    private int upsertDocuments(List<ProvinceDocumentRow> rows, boolean trackSeen) {
        if (rows.isEmpty()) {
            return 0;
        }
        List<ProvinceDocumentRow> copy = List.copyOf(rows);
        int[] affected = {0};
        controlPlaneTx.executeWithoutResult(tx -> {
            if (trackSeen) {
                controlPlane.batchUpdate(
                    "INSERT INTO province_sync_seen (tenant_id, document_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    copy, BATCH, (ps, r) -> {
                        ps.setString(1, r.getTenantId());
                        ps.setObject(2, r.getDocumentId());
                    });
            }
            int[][] counts = controlPlane.batchUpdate("""
                INSERT INTO province_documents
                  (tenant_id, document_id, doc_kind, owner_identifier, owner_name, code, title, document_type_name,
                   agency_identifier, agency_name, signer, priority, doc_date, promulgation_date, deadline_date,
                   status_code, process_state, is_overdue, is_internal, is_network, is_national, is_paper, is_signed,
                   is_revoked, source_send_log_id, search_text, created_on, source_modified_on, deleted, synced_on)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (tenant_id, document_id) DO UPDATE SET
                  doc_kind = EXCLUDED.doc_kind, owner_identifier = EXCLUDED.owner_identifier,
                  owner_name = EXCLUDED.owner_name, code = EXCLUDED.code, title = EXCLUDED.title,
                  document_type_name = EXCLUDED.document_type_name, agency_identifier = EXCLUDED.agency_identifier,
                  agency_name = EXCLUDED.agency_name, signer = EXCLUDED.signer, priority = EXCLUDED.priority,
                  doc_date = EXCLUDED.doc_date, promulgation_date = EXCLUDED.promulgation_date,
                  deadline_date = EXCLUDED.deadline_date, status_code = EXCLUDED.status_code,
                  process_state = EXCLUDED.process_state, is_overdue = EXCLUDED.is_overdue,
                  is_internal = CASE WHEN EXCLUDED.doc_kind = 'VBDI' THEN province_documents.is_internal ELSE EXCLUDED.is_internal END,
                  is_network = CASE WHEN EXCLUDED.doc_kind = 'VBDI' THEN province_documents.is_network ELSE EXCLUDED.is_network END,
                  is_national = CASE WHEN EXCLUDED.doc_kind = 'VBDI' THEN province_documents.is_national ELSE EXCLUDED.is_national END,
                  is_paper = EXCLUDED.is_paper, is_signed = EXCLUDED.is_signed, is_revoked = EXCLUDED.is_revoked,
                  source_send_log_id = EXCLUDED.source_send_log_id, search_text = EXCLUDED.search_text,
                  created_on = EXCLUDED.created_on, source_modified_on = EXCLUDED.source_modified_on,
                  deleted = EXCLUDED.deleted, synced_on = NOW()
                WHERE (province_documents.doc_kind, province_documents.owner_identifier, province_documents.owner_name,
                       province_documents.code, province_documents.title, province_documents.document_type_name,
                       province_documents.agency_identifier, province_documents.agency_name, province_documents.signer,
                       province_documents.priority, province_documents.doc_date, province_documents.promulgation_date,
                       province_documents.deadline_date, province_documents.status_code, province_documents.process_state,
                       province_documents.is_overdue, province_documents.is_paper, province_documents.is_signed,
                       province_documents.is_revoked, province_documents.source_send_log_id, province_documents.search_text,
                       province_documents.created_on, province_documents.source_modified_on, province_documents.deleted,
                       CASE WHEN EXCLUDED.doc_kind = 'VBDI' THEN NULL ELSE province_documents.is_internal END,
                       CASE WHEN EXCLUDED.doc_kind = 'VBDI' THEN NULL ELSE province_documents.is_network END,
                       CASE WHEN EXCLUDED.doc_kind = 'VBDI' THEN NULL ELSE province_documents.is_national END)
                  IS DISTINCT FROM
                      (EXCLUDED.doc_kind, EXCLUDED.owner_identifier, EXCLUDED.owner_name, EXCLUDED.code, EXCLUDED.title,
                       EXCLUDED.document_type_name, EXCLUDED.agency_identifier, EXCLUDED.agency_name, EXCLUDED.signer,
                       EXCLUDED.priority, EXCLUDED.doc_date, EXCLUDED.promulgation_date, EXCLUDED.deadline_date,
                       EXCLUDED.status_code, EXCLUDED.process_state, EXCLUDED.is_overdue, EXCLUDED.is_paper,
                       EXCLUDED.is_signed, EXCLUDED.is_revoked, EXCLUDED.source_send_log_id, EXCLUDED.search_text,
                       EXCLUDED.created_on, EXCLUDED.source_modified_on, EXCLUDED.deleted,
                       CASE WHEN EXCLUDED.doc_kind = 'VBDI' THEN NULL ELSE EXCLUDED.is_internal END,
                       CASE WHEN EXCLUDED.doc_kind = 'VBDI' THEN NULL ELSE EXCLUDED.is_network END,
                       CASE WHEN EXCLUDED.doc_kind = 'VBDI' THEN NULL ELSE EXCLUDED.is_national END)
                """, copy, BATCH, (ps, r) -> {
            ps.setString(1, r.getTenantId());
            ps.setObject(2, r.getDocumentId());
            ps.setString(3, r.getDocKind());
            ps.setString(4, ProvinceText.truncate(r.getOwnerIdentifier(), 200));
            ps.setString(5, ProvinceText.truncate(r.getOwnerName(), 500));
            ps.setString(6, ProvinceText.truncate(r.getCode(), 300));
            ps.setString(7, r.getTitle());
            ps.setString(8, ProvinceText.truncate(r.getDocumentTypeName(), 300));
            ps.setString(9, ProvinceText.truncate(r.getAgencyIdentifier(), 200));
            ps.setString(10, ProvinceText.truncate(r.getAgencyName(), 500));
            ps.setString(11, ProvinceText.truncate(r.getSigner(), 300));
            ps.setString(12, ProvinceText.truncate(r.getPriority(), 100));
            setDate(ps, 13, r.getDocDate());
            setDate(ps, 14, r.getPromulgationDate());
            setDate(ps, 15, r.getDeadlineDate());
            ps.setString(16, ProvinceText.truncate(r.getStatusCode(), 50));
            ps.setString(17, r.getProcessState());
            ps.setBoolean(18, r.isOverdue());
            ps.setBoolean(19, r.isInternal());
            ps.setBoolean(20, r.isNetwork());
            ps.setBoolean(21, r.isNational());
            ps.setBoolean(22, r.isPaper());
            ps.setBoolean(23, r.isSigned());
            ps.setBoolean(24, r.isRevoked());
            ps.setString(25, ProvinceText.truncate(r.getSourceSendLogId(), 100));
            ps.setString(26, r.getSearchText());
            ps.setObject(27, r.getCreatedOn());
            ps.setObject(28, r.getSourceModifiedOn());
            ps.setBoolean(29, r.isDeleted());
            });
            for (int[] batch : counts) {
                for (int c : batch) {
                    affected[0] += Math.max(c, 0);
                }
            }
        });
        return affected[0];
    }

    /** Câu SELECT bản ghi thay đổi, chỉ lấy đúng các key động cần dùng. */
    static String changedDocumentsSql(String table, String documentType, String[] keys, Timestamp since) {
        StringBuilder fields = new StringBuilder("jsonb_build_object(");
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) {
                fields.append(", ");
            }
            fields.append('\'').append(keys[i]).append("', dynamic_fields->'").append(keys[i]).append('\'');
        }
        fields.append(")::text AS f");
        String changedOn = "GREATEST(created_on, COALESCE(last_modified_on, created_on), COALESCE(deleted_on, created_on))";
        // Lọc tách OR từng cột (KHÔNG so GREATEST(...) > ?) để Postgres dùng được 3 chỉ mục sẵn có
        // ix_*_created_on / ix_*_last_modified_on / ix_*_deleted_on (BitmapOr) — tránh quét toàn bảng mỗi 5 phút.
        return "SELECT id, title, status, created_on, last_modified_on, deleted_on, " + changedOn + " AS changed_on, "
            + fields + " FROM " + table + " WHERE document_type = '" + documentType + "'"
            + (since == null ? "" : " AND (created_on > ? OR last_modified_on > ? OR deleted_on > ?)");
    }

    private void readTenant(String tenantId, java.util.function.Supplier<Void> work) {
        TenantContext.runAs(tenantId, () -> tenantReadTx.execute(status -> work.get()));
    }

    private JsonNode readFields(ResultSet rs) throws SQLException {
        String json = rs.getString("f");
        try {
            return json == null ? objectMapper.createObjectNode() : objectMapper.readTree(json);
        } catch (java.io.IOException ex) {
            return objectMapper.createObjectNode();
        }
    }

    private Map<String, String> loadOrganizationNames() {
        DataSource identity = identityDataSource.getIfAvailable();
        Map<String, String> names = new HashMap<>();
        if (identity == null) {
            return names;
        }
        try {
            TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(identity));
            tx.setReadOnly(true);
            tx.executeWithoutResult(status -> new JdbcTemplate(identity).query(
                "SELECT identifier, name FROM organizations WHERE deleted_on IS NULL AND identifier IS NOT NULL",
                rs -> {
                    names.putIfAbsent(rs.getString("identifier").trim(), rs.getString("name"));
                }));
        } catch (RuntimeException ex) {
            log.warn("Khong doc duoc ten to chuc cho kho tong hop toan tinh - ten don vi se trong", ex);
        }
        return names;
    }

    /** Mọi lệnh ghi control-plane phải nằm trong transaction: pool control-plane chạy autoCommit=false. */
    private int cpUpdate(String sql, Object... args) {
        Integer count = controlPlaneTx.execute(tx -> controlPlane.update(sql, args));
        return count == null ? 0 : count;
    }

    private void ensureState(String tenantId) {
        cpUpdate("INSERT INTO province_sync_state (tenant_id) VALUES (?) ON CONFLICT (tenant_id) DO NOTHING", tenantId);
    }

    private static boolean tryAdvisoryLock(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT pg_try_advisory_lock(" + ADVISORY_LOCK_KEY + ")")) {
            return rs.next() && rs.getBoolean(1);
        }
    }

    private static void advisoryUnlock(Connection connection) {
        try (Statement st = connection.createStatement()) {
            st.execute("SELECT pg_advisory_unlock(" + ADVISORY_LOCK_KEY + ")");
        } catch (SQLException ex) {
            log.warn("Khong nha duoc advisory lock dong bo tong hop (se tu nha khi dong ket noi)", ex);
        }
    }

    private static Object[] sinceArgs(Timestamp since) {
        return since == null ? new Object[0] : new Object[]{since};
    }

    private static Object[] docSinceArgs(Timestamp since) {
        return since == null ? new Object[0] : new Object[]{since, since, since};
    }

    private static void setDate(PreparedStatement ps, int index, LocalDate value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.DATE);
        } else {
            ps.setDate(index, Date.valueOf(value));
        }
    }

    private static Object toOffset(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toInstant().atZone(ZONE).toOffsetDateTime();
        }
        return value;
    }

    private static OffsetDateTime max(OffsetDateTime a, OffsetDateTime b) {
        if (a == null) {
            return b;
        }
        return b == null || !b.isAfter(a) ? a : b;
    }

    /** Nhãn kind dùng trong log. */
    static String kindLabel(String kind) {
        return ProvinceDocumentStates.KIND_VBDI.equals(kind) ? "vbdi" : "vbden";
    }
}
