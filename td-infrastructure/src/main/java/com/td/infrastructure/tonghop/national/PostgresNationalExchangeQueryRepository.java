package com.td.infrastructure.tonghop.national;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.td.application.tonghop.national.NationalExchangeDetail;
import com.td.application.tonghop.national.NationalExchangeItem;
import com.td.application.tonghop.national.NationalExchangeQueryRepository;
import com.td.application.tonghop.national.NationalExchangeSearchRequest;
import com.td.application.tonghop.national.NationalExchangeSearchResult;
import com.td.application.tonghop.national.NationalExchangeStates;
import com.td.application.tonghop.national.NationalExchangeSummary;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Đọc read model quản trị gửi nhận quốc gia của TD Gateway (DB riêng {@code tdgateway}) — danh sách đọc view
 * {@code vw_worker_national_exchange_search}, chi tiết đọc master/recipient/event/attempt/operation request
 * (tdgateway/docs/22, 23). Mọi điều kiện lọc qua tham số đặt tên; không ghi bảng nào.
 *
 * <p>Chỉ dùng cột có từ V8/V9 của gateway ở đường danh sách; {@code has_error} (V15) và {@code dynamic_fields}
 * (V16) được tính/đọc an toàn để màn không gãy khi gateway chưa chạy tới migration mới nhất.</p>
 *
 * <p>Bổ sung tên đơn vị (bảng {@code tenants}) và số/trích yếu văn bản (kho tổng hợp {@code province_documents})
 * từ control-plane — 2 DB khác nhau nên tra theo lô sau khi có trang kết quả.</p>
 */
@Slf4j
public class PostgresNationalExchangeQueryRepository implements NationalExchangeQueryRepository, AutoCloseable {

    /** Nhóm trạng thái hiển thị — giữ khớp {@link NationalExchangeStates}. */
    static final String STATE_SQL = """
        CASE WHEN e.status IN ('SENT','RECEIVED') THEN 'DONE'
             WHEN e.status = 'BUILT_ONLY' THEN 'BUILT_ONLY'
             WHEN e.status = 'QUARANTINED' THEN 'QUARANTINED'
             WHEN e.status IN ('FAILED','FAILED_RETRYABLE') THEN 'ERROR'
             ELSE 'IN_PROGRESS' END""";

    private static final String HAS_ERROR_SQL = "(e.error_code IS NOT NULL OR e.error_detail IS NOT NULL)";

    private static final int MAX_TIMELINE_ROWS = 300;
    private static final int MAX_OPERATION_ROWS = 50;

    /** Nhãn mã trạng thái eDXML (khớp {@code GuiNhanQuocGiaStatusCodes.INBOUND_LABELS} của gateway). */
    static final Map<String, String> REMOTE_STATUS_LABELS = Map.of(
        "01", "Đã đến", "02", "Từ chối tiếp nhận", "03", "Đã tiếp nhận", "04", "Phân công xử lý",
        "05", "Đang xử lý", "06", "Hoàn thành", "13", "Huỷ bỏ", "15", "Đã lấy lại",
        "16", "Từ chối yêu cầu cập nhật/thu hồi");

    /** Khoá dynamic_fields không trả ra trình duyệt (đường dẫn vật lý, bí mật) — tdgateway/docs/22 §2. */
    private static final List<String> HIDDEN_DYNAMIC_KEY_PARTS = List.of("path", "secret", "token", "password", "key");

    private final HikariDataSource gatewayDataSource;
    private final NamedParameterJdbcTemplate gateway;
    private final NamedParameterJdbcTemplate controlPlane;
    /** Pool control-plane autoCommit=false → đọc trong transaction chỉ đọc (như PostgresProvinceQueryRepository). */
    private final TransactionTemplate controlPlaneReadTx;
    private final ObjectMapper objectMapper;
    private final Cache<String, Map<String, String>> tenantNames = Caffeine.newBuilder()
        .maximumSize(1)
        .expireAfterWrite(Duration.ofMinutes(10))
        .build();

    /** Tra tên cơ quan Trục trên Redis trước SQL — {@code null} = chỉ tra SQL. */
    private final RedisNationalAgencyNames redisAgencyNames;

    public PostgresNationalExchangeQueryRepository(HikariDataSource gatewayDataSource, DataSource controlPlaneDataSource,
                                                   ObjectMapper objectMapper) {
        this(gatewayDataSource, controlPlaneDataSource, objectMapper, null);
    }

    public PostgresNationalExchangeQueryRepository(HikariDataSource gatewayDataSource, DataSource controlPlaneDataSource,
                                                   ObjectMapper objectMapper, RedisNationalAgencyNames redisAgencyNames) {
        this.redisAgencyNames = redisAgencyNames;
        this.gatewayDataSource = gatewayDataSource;
        this.gateway = new NamedParameterJdbcTemplate(gatewayDataSource);
        this.controlPlane = new NamedParameterJdbcTemplate(controlPlaneDataSource);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(controlPlaneDataSource));
        tx.setReadOnly(true);
        this.controlPlaneReadTx = tx;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------ Danh sách

    @Override
    public NationalExchangeSearchResult search(NationalExchangeSearchRequest r, LocalDate from, LocalDate to,
                                               int offset, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(r, from, to, true, params);
        Long total = gateway.queryForObject(
            "SELECT COUNT(*) FROM worker_national_exchange e WHERE " + where, params, Long.class);
        params.addValue("limit", limit).addValue("offset", offset);
        List<NationalExchangeItem> items = gateway.query("""
            SELECT e.exchange_id, e.direction, e.message_type, e.operation_type, e.status, e.tenant_id,
                   e.business_document_id, e.edxml_document_id, e.transport_document_id, e.center_document_id,
                   e.sender_agency_code, e.sender_agency_name, e.attempt_count, e.manual_replay, e.error_code,
                   e.error_category, e.error_detail, e.last_stage, e.last_event_type, e.content_length,
                   e.created_at, e.updated_at, e.completed_at,
                   """ + recipientColumns(r) + ", " + STATE_SQL + " AS state, " + HAS_ERROR_SQL + """
                    AS has_error
            FROM vw_worker_national_exchange_search e
            WHERE """ + " " + where + """
             ORDER BY e.updated_at DESC, e.exchange_id DESC
            LIMIT :limit OFFSET :offset""", params, (rs, i) -> mapItem(rs));
        enrichItems(items);
        return NationalExchangeSearchResult.builder()
            .items(items)
            .totalItems(total == null ? 0 : total)
            .build();
    }

    @Override
    public NationalExchangeSummary summary(NationalExchangeSearchRequest r, LocalDate from, LocalDate to) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(r, from, to, false, params);
        NationalExchangeSummary summary = gateway.query("""
            SELECT COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE x.direction='OUTBOUND') AS outbound,
                   COUNT(*) FILTER (WHERE x.direction='INBOUND') AS inbound,
                   COUNT(*) FILTER (WHERE x.state='DONE') AS done,
                   COUNT(*) FILTER (WHERE x.state='IN_PROGRESS') AS in_progress,
                   COUNT(*) FILTER (WHERE x.state='BUILT_ONLY') AS built_only,
                   COUNT(*) FILTER (WHERE x.state='ERROR') AS error,
                   COUNT(*) FILTER (WHERE x.state='QUARANTINED') AS quarantined,
                   COUNT(*) FILTER (WHERE x.direction='INBOUND' AND x.awaiting) AS inbound_awaiting,
                   COUNT(*) FILTER (WHERE x.needs_support) AS needs_support,
                   COALESCE(SUM(x.content_length), 0) AS total_bytes
            FROM (SELECT e.direction, e.content_length,
                         """ + needsSupportSql(r) + """
                 AS needs_support,
                         EXISTS (SELECT 1 FROM worker_national_exchange_recipient rr
                                 WHERE rr.exchange_id=e.exchange_id AND rr.delivery_status <> 'RECEIVED'"""
                + (r.getTenantId() == null ? "" : " AND rr.tenant_id = :tenantId") + """
                ) AS awaiting,
                         """ + STATE_SQL + """
                   AS state
                  FROM worker_national_exchange e WHERE """ + " " + where + ") x",
            params, rs -> {
                rs.next();
                return NationalExchangeSummary.builder()
                    .total(rs.getLong("total"))
                    .outbound(rs.getLong("outbound"))
                    .inbound(rs.getLong("inbound"))
                    .done(rs.getLong("done"))
                    .inProgress(rs.getLong("in_progress"))
                    .builtOnly(rs.getLong("built_only"))
                    .error(rs.getLong("error"))
                    .quarantined(rs.getLong("quarantined"))
                    .inboundAwaitingRecipients(rs.getLong("inbound_awaiting"))
                    .needsSupport(rs.getLong("needs_support"))
                    .staleHours(r.getStaleHours() == null ? 2 : r.getStaleHours())
                    .totalBytes(rs.getLong("total_bytes"))
                    .build();
            });
        summary.setDays(gateway.query("""
            SELECT CAST(e.updated_at AS date) AS day,
                   COUNT(*) FILTER (WHERE e.direction='OUTBOUND') AS outbound,
                   COUNT(*) FILTER (WHERE e.direction='INBOUND') AS inbound,
                   COUNT(*) FILTER (WHERE e.status IN ('FAILED','FAILED_RETRYABLE','QUARANTINED')) AS failed
            FROM worker_national_exchange e
            WHERE """ + " " + where + " GROUP BY 1 ORDER BY 1", params, (rs, i) -> NationalExchangeSummary.Day.builder()
            .day(rs.getObject("day", LocalDate.class))
            .outbound(rs.getLong("outbound"))
            .inbound(rs.getLong("inbound"))
            .failed(rs.getLong("failed"))
            .build()));
        return summary;
    }

    /**
     * Điều kiện chung (bí danh {@code e} = master hoặc view tìm kiếm — cùng cột lõi). Khoảng thời gian LUÔN có
     * để dùng chỉ mục {@code updated_at} (V15).
     */
    static String where(NationalExchangeSearchRequest r, LocalDate from, LocalDate to, boolean includeStates,
                        MapSqlParameterSource params) {
        List<String> clauses = new ArrayList<>();
        clauses.add("e.updated_at >= CAST(:fromDate AS date)");
        clauses.add("e.updated_at < CAST(:toDate AS date) + 1");
        params.addValue("fromDate", from.toString()).addValue("toDate", to.toString());
        if (r.getDirection() != null) {
            clauses.add("e.direction = :direction");
            params.addValue("direction", r.getDirection());
        }
        if (r.getMessageType() != null) {
            clauses.add("e.message_type = :messageType");
            params.addValue("messageType", r.getMessageType());
        }
        params.addValue("staleHours", r.getStaleHours() == null ? 2 : r.getStaleHours());
        if (includeStates && r.getStates() != null && !r.getStates().isEmpty()) {
            clauses.add("(" + STATE_SQL + ") IN (:states)");
            params.addValue("states", r.getStates());
        }
        if (includeStates && r.isNeedsSupport()) {
            clauses.add(needsSupportSql(r));
        }
        if (r.getBusinessDocumentId() != null) {
            clauses.add("e.business_document_id = :businessDocumentId");
            params.addValue("businessDocumentId", r.getBusinessDocumentId());
        }
        if (r.getTenantId() != null) {
            // Chiều đi: tenant gửi ở master; chiều đến: tenant nhận ở từng recipient (master có thể là "manual-replay").
            clauses.add("""
                (e.tenant_id = :tenantId OR EXISTS (SELECT 1 FROM worker_national_exchange_recipient tr
                    WHERE tr.exchange_id = e.exchange_id AND tr.tenant_id = :tenantId))""");
            params.addValue("tenantId", r.getTenantId());
        }
        if (r.getKeyword() != null) {
            clauses.add("""
                (e.exchange_id = :kw OR e.business_document_id = :kw OR e.center_document_id = :kw
                 OR e.edxml_document_id ILIKE :kwLike ESCAPE '\\' OR e.transport_document_id ILIKE :kwLike ESCAPE '\\'
                 OR e.sender_agency_code ILIKE :kwLike ESCAPE '\\' OR e.sender_agency_name ILIKE :kwLike ESCAPE '\\'
                 OR e.recipient_summary ILIKE :kwLike ESCAPE '\\'
                 OR EXISTS (SELECT 1 FROM worker_national_exchange_recipient kr WHERE kr.exchange_id = e.exchange_id
                     AND (kr.agency_code ILIKE :kwLike ESCAPE '\\' OR kr.agency_name ILIKE :kwLike ESCAPE '\\')))""");
            params.addValue("kw", r.getKeyword()).addValue("kwLike", "%" + escapeLike(r.getKeyword()) + "%");
        }
        return String.join(" AND ", clauses);
    }

    /**
     * Cột nơi nhận của dòng danh sách. Lọc theo tenant (bắt buộc với người dùng phạm vi đơn vị): gói đến chỉ đếm/liệt
     * kê nơi nhận của tenant đó — không lộ đơn vị khác cùng nhận 1 gói. Chiều đi giữ nguyên (cả gói thuộc tenant gửi).
     */
    static String recipientColumns(NationalExchangeSearchRequest r) {
        if (r.getTenantId() == null) {
            return "e.recipient_codes, e.recipient_count, e.acknowledged_count, e.recipient_summary";
        }
        String own = "FROM worker_national_exchange_recipient sr WHERE sr.exchange_id = e.exchange_id"
            + " AND sr.tenant_id = :tenantId";
        return "CASE WHEN e.direction = 'INBOUND' THEN (SELECT string_agg(sr.agency_code, ';' ORDER BY sr.agency_code) "
            + own + ") ELSE e.recipient_codes END AS recipient_codes, "
            + "CASE WHEN e.direction = 'INBOUND' THEN (SELECT COUNT(*) " + own + ") ELSE e.recipient_count END"
            + " AS recipient_count, "
            + "CASE WHEN e.direction = 'INBOUND' THEN (SELECT COUNT(*) " + own
            + " AND sr.delivery_status IN ('RECEIVED','SENT')) ELSE e.acknowledged_count END AS acknowledged_count, "
            + "CASE WHEN e.direction = 'INBOUND' THEN NULL ELSE e.recipient_summary END AS recipient_summary";
    }

    /**
     * "Gói cần hỗ trợ" (tdgateway/docs/22 §6): lỗi/cách ly; hoặc chưa xong mà không cập nhật quá {@code :staleHours} giờ;
     * hoặc gói đến còn đơn vị trong tỉnh chưa ACK quá ngưỡng (kể cả khi master đã RECEIVED — gặp thật với gói phát lại
     * thủ công). Phạm vi đơn vị: chỉ xét nơi nhận của tenant đó.
     */
    static String needsSupportSql(NationalExchangeSearchRequest r) {
        String ownRecipient = r.getTenantId() == null ? "" : " AND ns.tenant_id = :tenantId";
        return "(e.status IN ('FAILED','FAILED_RETRYABLE','QUARANTINED')"
            + " OR (e.status NOT IN ('SENT','RECEIVED','BUILT_ONLY')"
            + " AND e.updated_at < NOW() - make_interval(hours => :staleHours))"
            + " OR (e.direction = 'INBOUND' AND EXISTS (SELECT 1 FROM worker_national_exchange_recipient ns"
            + " WHERE ns.exchange_id = e.exchange_id AND ns.delivery_status <> 'RECEIVED'"
            + " AND ns.updated_at < NOW() - make_interval(hours => :staleHours)" + ownRecipient + ")))";
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static NationalExchangeItem mapItem(ResultSet rs) throws SQLException {
        String recipientCodes = rs.getString("recipient_codes");
        return NationalExchangeItem.builder()
            .exchangeId(rs.getString("exchange_id"))
            .direction(rs.getString("direction"))
            .messageType(rs.getString("message_type"))
            .operationType(rs.getString("operation_type"))
            .status(rs.getString("status"))
            .state(rs.getString("state"))
            .tenantId(rs.getString("tenant_id"))
            .businessDocumentId(rs.getString("business_document_id"))
            .edxmlDocumentId(rs.getString("edxml_document_id"))
            .transportDocumentId(rs.getString("transport_document_id"))
            .centerDocumentId(rs.getString("center_document_id"))
            .senderAgencyCode(rs.getString("sender_agency_code"))
            .senderAgencyName(rs.getString("sender_agency_name"))
            .recipientCodes(recipientCodes != null ? recipientCodes : rs.getString("recipient_summary"))
            .recipientCount(rs.getInt("recipient_count"))
            .acknowledgedCount(rs.getInt("acknowledged_count"))
            .attemptCount(rs.getInt("attempt_count"))
            .manualReplay(rs.getBoolean("manual_replay"))
            .hasError(rs.getBoolean("has_error"))
            .errorCode(rs.getString("error_code"))
            .errorCategory(rs.getString("error_category"))
            .errorDetail(rs.getString("error_detail"))
            .lastStage(rs.getString("last_stage"))
            .lastEventType(rs.getString("last_event_type"))
            .contentLength(rs.getObject("content_length", Long.class))
            .createdAt(rs.getObject("created_at", OffsetDateTime.class))
            .updatedAt(rs.getObject("updated_at", OffsetDateTime.class))
            .completedAt(rs.getObject("completed_at", OffsetDateTime.class))
            .build();
    }

    // ------------------------------------------------------------------ Chi tiết

    @Override
    public Optional<NationalExchangeDetail> findDetail(String exchangeId) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", exchangeId);
        // to_jsonb: đọc cả cột của migration mới (V14/V16) nếu có mà không gãy khi gateway chưa migrate tới đó.
        List<Map<String, Object>> rows = gateway.query("""
            SELECT to_jsonb(e) AS row_json, """ + STATE_SQL + " AS state, " + HAS_ERROR_SQL + """
                    AS has_error
            FROM vw_worker_national_exchange_search e WHERE e.exchange_id = :id""", params,
            (rs, i) -> {
                Map<String, Object> row = readJson(rs.getString("row_json"));
                row.put("__state", rs.getString("state"));
                row.put("__has_error", rs.getBoolean("has_error"));
                return row;
            });
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.get(0);
        NationalExchangeItem item = itemFromJson(row);
        enrichItems(List.of(item));

        List<NationalExchangeDetail.Recipient> recipients = gateway.query("""
            SELECT tenant_id, agency_code, agency_name, organization_identifier, delivery_status, last_status_code,
                   last_status_label, created_document_id, acknowledged_at, error_detail, updated_at
            FROM worker_national_exchange_recipient WHERE exchange_id = :id
            ORDER BY tenant_id, agency_code""", params, (rs, i) -> {
            String code = rs.getString("last_status_code");
            String label = rs.getString("last_status_label");
            return NationalExchangeDetail.Recipient.builder()
                .tenantId(emptyToNull(rs.getString("tenant_id")))
                .agencyCode(rs.getString("agency_code"))
                .agencyName(rs.getString("agency_name"))
                .organizationIdentifier(rs.getString("organization_identifier"))
                .deliveryStatus(rs.getString("delivery_status"))
                .lastStatusCode(code)
                .lastStatusLabel(label != null ? label : code == null ? null : REMOTE_STATUS_LABELS.get(code))
                .createdDocumentId(rs.getString("created_document_id"))
                .acknowledgedAt(rs.getObject("acknowledged_at", OffsetDateTime.class))
                .errorDetail(rs.getString("error_detail"))
                .updatedAt(rs.getObject("updated_at", OffsetDateTime.class))
                .build();
        });
        enrichRecipients(recipients);

        params.addValue("limit", MAX_TIMELINE_ROWS);
        List<NationalExchangeDetail.Event> events = gateway.query("""
            SELECT event_id, event_type, status_before, status_after, tenant_id, agency_code, actor_type, actor_name,
                   details, occurred_at
            FROM worker_national_exchange_event WHERE exchange_id = :id
            ORDER BY occurred_at DESC, event_id DESC LIMIT :limit""", params,
            (rs, i) -> NationalExchangeDetail.Event.builder()
                .eventId(rs.getLong("event_id"))
                .eventType(rs.getString("event_type"))
                .statusBefore(rs.getString("status_before"))
                .statusAfter(rs.getString("status_after"))
                .tenantId(rs.getString("tenant_id"))
                .agencyCode(rs.getString("agency_code"))
                .actorType(rs.getString("actor_type"))
                .actorName(rs.getString("actor_name"))
                .details(rs.getString("details"))
                .occurredAt(rs.getObject("occurred_at", OffsetDateTime.class))
                .build());

        List<NationalExchangeDetail.Attempt> attempts = gateway.query("""
            SELECT attempt_id, operation, outcome, attempt_no, requested_transport_status,
                   resulting_transport_status, error_code, error_category, error_retryable, error_detail,
                   started_at, completed_at
            FROM worker_national_exchange_attempt WHERE exchange_id = :id
            ORDER BY completed_at DESC, attempt_id DESC LIMIT :limit""", params,
            (rs, i) -> NationalExchangeDetail.Attempt.builder()
                .attemptId(rs.getLong("attempt_id"))
                .operation(rs.getString("operation"))
                .outcome(rs.getString("outcome"))
                .attemptNo(rs.getObject("attempt_no", Integer.class))
                .requestedTransportStatus(rs.getString("requested_transport_status"))
                .resultingTransportStatus(rs.getString("resulting_transport_status"))
                .errorCode(rs.getString("error_code"))
                .errorCategory(rs.getString("error_category"))
                .errorRetryable(rs.getObject("error_retryable", Boolean.class))
                .errorDetail(rs.getString("error_detail"))
                .startedAt(rs.getObject("started_at", OffsetDateTime.class))
                .completedAt(rs.getObject("completed_at", OffsetDateTime.class))
                .build());

        params.addValue("opLimit", MAX_OPERATION_ROWS);
        List<NationalExchangeDetail.Operation> operations = gateway.query(OPERATION_SELECT
            + " WHERE exchange_id = :id ORDER BY requested_at DESC LIMIT :opLimit", params,
            (rs, i) -> mapOperation(rs));

        // dynamic_fields đọc từ BẢNG: view V15 tạo trước khi V16 thêm cột nên e.* của view KHÔNG có cột này.
        // to_jsonb để không gãy khi gateway chưa chạy V16.
        Object dynamicFields = gateway.query(
            "SELECT to_jsonb(x)->'dynamic_fields' AS d FROM worker_national_exchange x WHERE x.exchange_id = :id",
            params, rs -> rs.next() ? readJsonValue(rs.getString("d")) : null);

        return Optional.of(NationalExchangeDetail.builder()
            .exchange(item)
            .contentSha256(str(row, "content_sha256"))
            .transportStatus(str(row, "transport_status"))
            .recoveryStatus(str(row, "recovery_status"))
            .errorRetryable(row.get("error_retryable") instanceof Boolean b ? b : null)
            .successDetail(str(row, "success_detail"))
            .transportAckRequired(!Boolean.FALSE.equals(row.get("transport_ack_required")))
            .occurredAt(time(row, "occurred_at"))
            .dynamicFields(visibleDynamicFields(dynamicFields))
            .statusReference(statusReference(dynamicFields))
            .recipients(recipients)
            .events(events)
            .attempts(attempts)
            .operations(operations)
            .build());
    }

    /** Dùng chung với {@link PostgresNationalExchangeCommandRepository}. */
    static final String OPERATION_SELECT = """
        SELECT request_id, exchange_id, operation_type, status, reason, requested_by, requested_by_name, error_detail,
               requested_at, completed_at, result_json->>'message' AS result_message,
               result_json->>'newExchangeId' AS new_exchange_id
          FROM worker_national_exchange_operation_request""";

    static NationalExchangeDetail.Operation mapOperation(ResultSet rs) throws SQLException {
        return NationalExchangeDetail.Operation.builder()
            .requestId(rs.getString("request_id"))
            .exchangeId(rs.getString("exchange_id"))
            .operationType(rs.getString("operation_type"))
            .status(rs.getString("status"))
            .resultMessage(rs.getString("result_message"))
            .newExchangeId(rs.getString("new_exchange_id"))
            .reason(rs.getString("reason"))
            .requestedBy(rs.getString("requested_by"))
            .requestedByName(rs.getString("requested_by_name"))
            .errorDetail(rs.getString("error_detail"))
            .requestedAt(rs.getObject("requested_at", OffsetDateTime.class))
            .completedAt(rs.getObject("completed_at", OffsetDateTime.class))
            .build();
    }

    private NationalExchangeItem itemFromJson(Map<String, Object> row) {
        String recipientCodes = str(row, "recipient_codes");
        return NationalExchangeItem.builder()
            .exchangeId(str(row, "exchange_id"))
            .direction(str(row, "direction"))
            .messageType(str(row, "message_type"))
            .operationType(str(row, "operation_type"))
            .status(str(row, "status"))
            .state((String) row.get("__state"))
            .tenantId(str(row, "tenant_id"))
            .businessDocumentId(str(row, "business_document_id"))
            .edxmlDocumentId(str(row, "edxml_document_id"))
            .transportDocumentId(str(row, "transport_document_id"))
            .centerDocumentId(str(row, "center_document_id"))
            .senderAgencyCode(str(row, "sender_agency_code"))
            .senderAgencyName(str(row, "sender_agency_name"))
            .recipientCodes(recipientCodes != null ? recipientCodes : str(row, "recipient_summary"))
            .recipientCount(num(row, "recipient_count"))
            .acknowledgedCount(num(row, "acknowledged_count"))
            .attemptCount(num(row, "attempt_count"))
            .manualReplay(Boolean.TRUE.equals(row.get("manual_replay")))
            .hasError(Boolean.TRUE.equals(row.get("__has_error")))
            .errorCode(str(row, "error_code"))
            .errorCategory(str(row, "error_category"))
            .errorDetail(str(row, "error_detail"))
            .lastStage(str(row, "last_stage"))
            .lastEventType(str(row, "last_event_type"))
            .contentLength(row.get("content_length") instanceof Number n ? n.longValue() : null)
            .createdAt(time(row, "created_at"))
            .updatedAt(time(row, "updated_at"))
            .completedAt(time(row, "completed_at"))
            .build();
    }

    // ------------------------------------------------------------------ Bổ sung từ control-plane

    private void enrichItems(List<NationalExchangeItem> items) {
        if (items.isEmpty()) {
            return;
        }
        Map<String, String> names = tenantNames();
        Set<UUID> documentIds = new HashSet<>();
        for (NationalExchangeItem item : items) {
            item.setTenantName(names.get(item.getTenantId()));
            if (NationalExchangeStates.DIRECTION_OUTBOUND.equals(item.getDirection())) {
                UUID id = uuid(item.getBusinessDocumentId());
                if (id != null) {
                    documentIds.add(id);
                }
            }
        }
        Map<String, String[]> documents = documents(documentIds);
        for (NationalExchangeItem item : items) {
            String[] doc = documents.get(item.getTenantId() + "|" + item.getBusinessDocumentId());
            if (doc != null && NationalExchangeStates.DIRECTION_OUTBOUND.equals(item.getDirection())) {
                item.setDocumentCode(doc[0]);
                item.setDocumentTitle(doc[1]);
            }
        }
        // Tên cơ quan (danh bạ Trục): cơ quan gửi ngoài tỉnh + nơi nhận.
        Set<String> codes = new HashSet<>();
        for (NationalExchangeItem item : items) {
            codes.add(item.getSenderAgencyCode());
            codes.addAll(splitCodes(item.getRecipientCodes()));
        }
        Map<String, String> agencies = agencyNames(codes);
        if (agencies.isEmpty()) {
            return;
        }
        for (NationalExchangeItem item : items) {
            if (item.getSenderAgencyName() == null && item.getSenderAgencyCode() != null) {
                item.setSenderAgencyName(agencies.get(item.getSenderAgencyCode()));
            }
            List<String> recipientCodes = splitCodes(item.getRecipientCodes());
            if (!recipientCodes.isEmpty()) {
                item.setRecipientNames(String.join(";", recipientCodes.stream()
                    .map(c -> agencies.getOrDefault(c, c)).toList()));
            }
        }
    }

    private static List<String> splitCodes(String codes) {
        if (codes == null || codes.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String c : codes.split("[;,]")) {
            if (!c.isBlank()) {
                out.add(c.trim());
            }
        }
        return out;
    }

    /**
     * Mã → tên cơ quan theo danh bạ Trục ({@code worker_national_agency}, gateway V17). Bảng chưa có (gateway chưa
     * migrate)/lỗi → rỗng, màn vẫn chạy với mã.
     *
     * <p>22/10/2026: tra Redis trước ({@link RedisNationalAgencyNames}, gateway đẩy lên), chỉ mã Redis không có mới
     * tra SQL — danh bạ có thể > 100k cơ quan.</p>
     */
    private Map<String, String> agencyNames(Collection<String> codes) {
        List<String> wanted = codes.stream().filter(c -> c != null && !c.isBlank()).distinct().limit(1000).toList();
        if (wanted.isEmpty()) {
            return Map.of();
        }
        Map<String, String> map = new HashMap<>(
            redisAgencyNames == null ? Map.of() : redisAgencyNames.lookup(wanted));
        List<String> missing = wanted.stream().filter(c -> !map.containsKey(c)).toList();
        if (missing.isEmpty()) {
            return map;
        }
        try {
            gateway.query("SELECT agency_code, agency_name FROM worker_national_agency WHERE agency_code IN (:codes)",
                new MapSqlParameterSource("codes", missing), rs -> {
                    if (rs.getString("agency_name") != null) {
                        map.put(rs.getString("agency_code"), rs.getString("agency_name"));
                    }
                });
            return map;
        } catch (RuntimeException ex) {
            log.debug("Chưa tra được danh bạ cơ quan Trục: {}", ex.getMessage());
            return map;
        }
    }

    /** Gói văn bản đi được 1 gói trạng thái phản hồi: khớp DocumentId eDXML (gateway ghi khi gửi thành công). */
    private String relatedOutboundExchange(String edxmlDocumentId) {
        if (edxmlDocumentId == null || edxmlDocumentId.isBlank()) {
            return null;
        }
        List<String> ids = gateway.query("""
            SELECT exchange_id FROM worker_national_exchange
             WHERE direction = 'OUTBOUND' AND message_type = 'edoc' AND edxml_document_id = :d
             ORDER BY created_at DESC LIMIT 1""", new MapSqlParameterSource("d", edxmlDocumentId),
            (rs, i) -> rs.getString(1));
        return ids.isEmpty() ? null : ids.get(0);
    }

    private NationalExchangeDetail.StatusReference statusReference(Object dynamicFields) {
        if (!(dynamicFields instanceof Map<?, ?> map) || (map.get("statusRefDocumentId") == null
                && map.get("statusCode") == null)) {
            return null;
        }
        String documentId = asString(map.get("statusRefDocumentId"));
        String code = asString(map.get("statusCode"));
        return NationalExchangeDetail.StatusReference.builder()
            .documentId(documentId)
            .code(asString(map.get("statusRefCode")))
            .statusCode(code)
            .statusLabel(code == null ? null : REMOTE_STATUS_LABELS.get(code))
            .description(asString(map.get("statusDescription")))
            .fromOrganId(asString(map.get("statusFromOrganId")))
            .fromOrganName(asString(map.get("statusFromOrganName")))
            .relatedExchangeId(relatedOutboundExchange(documentId))
            .build();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private void enrichRecipients(List<NationalExchangeDetail.Recipient> recipients) {
        if (recipients.isEmpty()) {
            return;
        }
        Map<String, String> names = tenantNames();
        Set<UUID> documentIds = new HashSet<>();
        for (NationalExchangeDetail.Recipient r : recipients) {
            r.setTenantName(r.getTenantId() == null ? null : names.get(r.getTenantId()));
            UUID id = uuid(r.getCreatedDocumentId());
            if (id != null) {
                documentIds.add(id);
            }
        }
        Map<String, String[]> documents = documents(documentIds);
        for (NationalExchangeDetail.Recipient r : recipients) {
            String[] doc = documents.get(r.getTenantId() + "|" + r.getCreatedDocumentId());
            if (doc != null) {
                r.setCreatedDocumentCode(doc[0]);
                r.setCreatedDocumentTitle(doc[1]);
            }
        }
        // Nơi nhận ngoài tỉnh (chiều đi) thường thiếu tên — tra danh bạ Trục.
        Map<String, String> agencies = agencyNames(recipients.stream()
            .filter(r -> r.getAgencyName() == null).map(NationalExchangeDetail.Recipient::getAgencyCode).toList());
        recipients.forEach(r -> {
            if (r.getAgencyName() == null) {
                r.setAgencyName(agencies.get(r.getAgencyCode()));
            }
        });
    }

    /** "tenant|documentId" → [số/ký hiệu, trích yếu]. Lỗi control-plane chỉ làm thiếu phần bổ sung, không hỏng màn. */
    private Map<String, String[]> documents(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            return controlPlaneReadTx.execute(status -> {
                Map<String, String[]> map = new HashMap<>();
                controlPlane.query("""
                    SELECT tenant_id, document_id, code, title FROM province_documents
                    WHERE document_id IN (:ids)""", new MapSqlParameterSource("ids", ids), rs -> {
                    map.put(rs.getString("tenant_id") + "|" + rs.getString("document_id"),
                        new String[] {rs.getString("code"), rs.getString("title")});
                });
                return map;
            });
        } catch (RuntimeException ex) {
            log.warn("Khong tra duoc so/trich yeu van ban tu kho tong hop: {}", ex.getMessage());
            return Map.of();
        }
    }

    @Override
    public String tenantDisplayName(String tenantId) {
        return tenantId == null ? null : tenantNames().get(tenantId);
    }

    private Map<String, String> tenantNames() {
        Map<String, String> cached = tenantNames.getIfPresent("all");
        if (cached != null) {
            return cached;
        }
        try {
            Map<String, String> loaded = controlPlaneReadTx.execute(status -> {
                Map<String, String> map = new LinkedHashMap<>();
                controlPlane.query("SELECT tenant_id, display_name FROM tenants", new MapSqlParameterSource(),
                    rs -> {
                        map.put(rs.getString("tenant_id"), rs.getString("display_name"));
                    });
                return map;
            });
            Map<String, String> result = loaded == null ? Map.of() : loaded;
            tenantNames.put("all", result);
            return result;
        } catch (RuntimeException ex) {
            log.warn("Khong doc duoc danh sach tenant: {}", ex.getMessage());
            return Map.of();
        }
    }

    // ------------------------------------------------------------------ Tiện ích

    private Map<String, Object> readJson(String json) {
        if (json == null) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<HashMap<String, Object>>() { });
        } catch (Exception ex) {
            throw new IllegalStateException("Khong doc duoc ban ghi goi trao doi", ex);
        }
    }

    private Object readJsonValue(String json) {
        return json == null || json.isBlank() || "null".equals(json) ? null : readJson(json);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> visibleDynamicFields(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> visible = new LinkedHashMap<>();
        ((Map<String, Object>) map).forEach((key, v) -> {
            String lower = key.toLowerCase(Locale.ROOT);
            if (HIDDEN_DYNAMIC_KEY_PARTS.stream().noneMatch(lower::contains)) {
                visible.put(key, v);
            }
        });
        return visible;
    }

    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : v.toString();
    }

    private static int num(Map<String, Object> row, String key) {
        return row.get(key) instanceof Number n ? n.intValue() : 0;
    }

    private static OffsetDateTime time(Map<String, Object> row, String key) {
        String v = str(row, key);
        if (v == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(v);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static UUID uuid(String value) {
        if (value == null || value.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    @Override
    public void close() {
        gatewayDataSource.close();
    }
}
