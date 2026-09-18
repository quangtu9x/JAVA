package com.td.worker.tasks.vbden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.worker.config.WorkerProperties;
import com.td.worker.core.task.TaskContext;
import com.td.worker.core.task.TaskHandler;
import com.td.worker.core.task.UnitResult;
import com.td.worker.infrastructure.tenant.TenantDataSourceManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Task {@code vbden.forward_unit.reply} — port {@code DocumentInProcessUnit.java#copyToDocumentIn()}
 * (Domino, phần xử lý TẠI TENANT GỐC sau khi nhận reply xác nhận đơn vị con đã tạo bản sao — xem
 * javadoc {@link VbdenForwardUnitTaskHandler}). Chạy TRONG tenant GỐC (đơn vị vừa "chuyển xử lý").
 *
 * <p>Domino gốc ({@code TDCommons.removeToFieldListStringHideVBden}) loại người/đơn vị được giao khỏi
 * {@code user_processing}/{@code user_process}/{@code user_coordinate}/{@code user_to_read}, thêm vào
 * {@code user_processed}. Bản port này CHỈ xử lý {@code unit_process} (field mà
 * {@code ChuyenXuLyVbdenUseCase#publishForwardUnitIfCrossTenant} dùng để phát hiện cross-tenant — xem
 * javadoc đó) — CHƯA đụng 3 field còn lại (`user_process`/`user_coordinate`/`user_to_read` ở mức
 * USERNAME cụ thể, không phải mức ĐƠN VỊ) vì luồng hiện tại chỉ kích hoạt theo {@code unit_process}
 * (đơn vị), không có username cá nhân nào cần loại riêng ở bước này.</p>
 *
 * <p>Ghi thêm {@code forwarded_units} (mảng {@code {identifier, tenantId, documentId}}, APPEND-ONLY —
 * không bao giờ xoá phần tử cũ) lên văn bản GỐC — chuẩn bị dữ liệu liên kết cho revoke cascade (giai
 * đoạn 2, xem javadoc {@code ChuyenXuLyVbdenUseCase#publishForwardUnitIfCrossTenant} — CHƯA port lần
 * này, chỉ ghi vết để không phải đổi schema lại khi port tiếp).</p>
 *
 * <p><b>Bổ sung 08/09/2026 — yêu cầu người dùng: "muốn phản hồi trạng thái 'Đã đến'... vào quá trình
 * xử lý"</b>: {@link #insertArrivalHistoryRow} ghi THÊM 1 dòng {@code vbden_res_documents}
 * ({@code parentid = originDocumentId}, action {@code "da_den_don_vi_khac"}) NGAY trong CÙNG giao
 * dịch với 2 bước trên — để văn bản gốc hiện được "Đã đến đơn vị X" ở tab "Quá trình xử lý"
 * ({@code QuaTrinhXuLy}, đọc {@code vbden_res_documents} theo {@code parentid}) ngay khi đơn vị con
 * xác nhận đã tạo bản sao, KHÔNG cần đăng nhập sang tenant khác để biết. Đây là lần ghi DUY NHẤT (xảy
 * ra 1 lần lúc tạo bản sao) — các hành động xử lý TIẾP THEO tại đơn vị con được echo ngược bởi 1 cơ
 * chế RIÊNG, xem {@link VbdenForwardUnitStatusTaskHandler}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VbdenForwardUnitReplyTaskHandler implements TaskHandler {

    private static final String UNIT_PROCESS_FIELD = "unit_process";
    private static final String FORWARDED_UNITS_FIELD = "forwarded_units";

    private final WorkerProperties props;
    private final TenantDataSourceManager tenantDataSources;
    private final ObjectMapper objectMapper;

    @Override
    public String taskType() {
        return VbdenTaskConstants.TASK_FORWARD_UNIT_REPLY;
    }

    @Override
    public UnitResult execute(TaskContext context, JsonNode payload) {
        if (!props.getTasks().getVbden().isEnabled()) {
            return UnitResult.failure("worker.tasks.vbden.enabled=false");
        }
        final VbdenForwardUnitReplyPayload p;
        try {
            p = objectMapper.treeToValue(payload, VbdenForwardUnitReplyPayload.class);
        } catch (Exception e) {
            return UnitResult.failure("payload vbden.forward_unit.reply không hợp lệ: " + e.getMessage());
        }
        if (p == null || p.getOriginDocumentId() == null || p.getOriginDocumentId().isBlank()
                || p.getTargetIdentifier() == null || p.getTargetIdentifier().isBlank()) {
            return UnitResult.failure(
                "payload vbden.forward_unit.reply thiếu field bắt buộc (originDocumentId/targetIdentifier)");
        }
        UUID originDocumentId;
        try {
            originDocumentId = UUID.fromString(p.getOriginDocumentId().trim());
        } catch (Exception e) {
            return UnitResult.failure("originDocumentId khong hop le: " + p.getOriginDocumentId());
        }

        log.info("vbden.forward_unit.reply: originDocumentId={}, target={}, createdDocumentId={} (tenant tao {})",
            p.getOriginDocumentId(), p.getTargetIdentifier(), p.getCreatedDocumentId(), p.getCreatedTenantId());

        var dsOpt = tenantDataSources.getDataSource(context.tenantId());
        if (dsOpt.isEmpty()) {
            return UnitResult.failure(
                "Không có connection pool cho tenant " + context.tenantId()
                    + " (kiểm tra control-plane tenants registry / đường mạng tới DB tenant)");
        }
        DataSource ds = dsOpt.get();
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        Boolean updated = tx.execute(status -> {
            boolean ok = updateOriginDocument(jdbc, originDocumentId, p);
            if (ok) {
                insertArrivalHistoryRow(jdbc, originDocumentId, p);
            }
            return ok;
        });
        if (!Boolean.TRUE.equals(updated)) {
            log.warn("vbden.forward_unit.reply: khong tim thay van ban goc id={} tai tenant {} (co the da bi xoa)",
                originDocumentId, context.tenantId());
        }
        return UnitResult.success();
    }

    private boolean updateOriginDocument(JdbcTemplate jdbc, UUID originDocumentId, VbdenForwardUnitReplyPayload p) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT dynamic_fields::text AS dynamic_fields FROM documents WHERE id = ? AND document_type = 'vbden'",
            originDocumentId);
        if (rows.isEmpty()) {
            return false;
        }
        String currentJson = (String) rows.get(0).get("dynamic_fields");
        ObjectNode fields;
        try {
            JsonNode parsed = objectMapper.readTree(currentJson == null || currentJson.isBlank() ? "{}" : currentJson);
            fields = parsed.isObject() ? (ObjectNode) parsed : objectMapper.createObjectNode();
        } catch (Exception ex) {
            fields = objectMapper.createObjectNode();
        }

        removeFromUnitProcess(fields, p.getTargetIdentifier());
        appendForwardedUnit(fields, p);

        try {
            String updatedJson = objectMapper.writeValueAsString(fields);
            jdbc.update("UPDATE documents SET dynamic_fields = ?::jsonb WHERE id = ?", updatedJson, originDocumentId);
            return true;
        } catch (Exception ex) {
            log.warn("Loi ghi lai dynamic_fields cho van ban goc {}: {}", originDocumentId, ex.getMessage());
            return false;
        }
    }

    /**
     * {@code unit_process} luôn được {@code ChuyenXuLyVbdenUseCase#updateChutri} ghi dạng JSON array
     * (List<String>) — nhưng đọc "khoan dung" cả dạng scalar ";"-nối (cùng nguyên tắc
     * {@code readMultiValue} nhiều nơi trong td-application) để an toàn nếu dữ liệu cũ khác quy ước.
     * LUÔN ghi lại dưới dạng array, đúng quy ước hiện có.
     */
    private void removeFromUnitProcess(ObjectNode fields, String identifierToRemove) {
        JsonNode raw = fields.get(UNIT_PROCESS_FIELD);
        Set<String> values = new LinkedHashSet<>();
        if (raw != null && raw.isArray()) {
            for (JsonNode item : raw) {
                String value = item.asText("").trim();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        } else if (raw != null && !raw.isNull()) {
            for (String part : raw.asText("").split(";")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
        }
        values.remove(identifierToRemove);
        ArrayNode array = objectMapper.createArrayNode();
        values.forEach(array::add);
        fields.set(UNIT_PROCESS_FIELD, array);
    }

    private void appendForwardedUnit(ObjectNode fields, VbdenForwardUnitReplyPayload p) {
        ArrayNode forwardedUnits;
        JsonNode existing = fields.get(FORWARDED_UNITS_FIELD);
        if (existing != null && existing.isArray()) {
            forwardedUnits = (ArrayNode) existing;
        } else {
            forwardedUnits = objectMapper.createArrayNode();
        }
        ObjectNode entry = objectMapper.createObjectNode();
        entry.put("identifier", p.getTargetIdentifier());
        entry.put("tenantId", p.getCreatedTenantId());
        entry.put("documentId", p.getCreatedDocumentId());
        forwardedUnits.add(entry);
        fields.set(FORWARDED_UNITS_FIELD, forwardedUnits);
    }

    /**
     * Bổ sung 08/09/2026 (xem javadoc lớp) — ghi 1 dòng {@code vbden_res_documents} dạng
     * {@code parentid/action/opinion/creator/nguoi_thuc_hien/identifier} ĐÚNG shape
     * {@code ChuyenXuLyVbdenUseCase#createProcessingRecord} đang dùng (để {@code QuaTrinhXuLy}/
     * {@code LichSuXuLyUseCase} đọc lại được như mọi vết xử lý khác, không cần biết đây là dòng do
     * cơ chế cross-tenant tạo ra).
     */
    private void insertArrivalHistoryRow(JdbcTemplate jdbc, UUID originDocumentId, VbdenForwardUnitReplyPayload p) {
        String displayName = (p.getTargetName() == null || p.getTargetName().isBlank())
            ? p.getTargetIdentifier() : p.getTargetName();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("parentid", originDocumentId.toString());
        fields.put("action", "da_den_don_vi_khac");
        fields.put("opinion", "Đã đến đơn vị " + displayName);
        fields.put("nguoi_thuc_hien", displayName);
        fields.put("creator", displayName);
        if (p.getTargetIdentifier() != null && !p.getTargetIdentifier().isBlank()) {
            fields.put("identifier", p.getTargetIdentifier());
        }
        String dynamicFieldsJson;
        try {
            dynamicFieldsJson = objectMapper.writeValueAsString(fields);
        } catch (Exception ex) {
            dynamicFieldsJson = "{}";
        }
        jdbc.update("""
                INSERT INTO vbden_res_documents
                 (id, title, document_type, status, tags_json, attributes_json, metadata_json,
                  dynamic_fields, version_no, created_on)
                VALUES (?, 'Đã đến', 'vbden_res', 'DRAFT', '[]', '{}', '{}', ?::jsonb, 1, CURRENT_TIMESTAMP)
                """,
            UUID.randomUUID(), dynamicFieldsJson);
    }
}
