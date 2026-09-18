package com.td.worker.tasks.vbden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.UUID;

/**
 * Task {@code vbden.forward_unit.revoke} — giai đoạn 2 của {@code vbden.forward_unit} (yêu cầu người
 * dùng 08/09/2026: "giai đoạn 1 cơ bản đã xong, làm giai đoạn 2 LayLaiVanBanUseCase/ThuHoiVanBanUseCase").
 * Chạy TẠI TENANT ĐÍCH (đơn vị con đang giữ bản sao "văn bản đến" được tạo bởi
 * {@link VbdenForwardUnitTaskHandler}) — publish từ {@code PublishForwardUnitRevokeUseCase}
 * (td-application, dùng chung bởi {@code LayLaiVanBanUseCase}/{@code ThuHoiVanBanUseCase}) khi văn bản
 * GỐC bị "Lấy lại VB"/"Thu hồi" và đã từng "chuyển xử lý" cho đơn vị này.
 *
 * <p><b>Đánh dấu bản sao là "đã lấy lại/thu hồi"</b> — CÙNG khuôn {@link VbdiRevokeTaskHandler}
 * (module {@code vbdi}): set {@code reply="1"}/{@code is_revoke="1"}/{@code delete_date} — field
 * {@code reply="1"} là điều kiện loại trừ CHUNG mà {@code ViewQueryService#quamangnoiboFilter}/
 * {@code lienthongFilter}/{@code dendientuFilter}/{@code denquamangFilter} đã dùng SẴN (bản sao vốn
 * hiển thị ở các view này nhờ {@code contact_unit_identifier}/{@code document_internet}, xem
 * {@code VbdenForwardUnitTaskHandler#insertForwardedCopy}, bổ sung (3)). NGOÀI RA — khác
 * {@code vbdi.revoke} — bản sao này còn hiển thị ở view "chờ xử lý"
 * ({@code chophanxulyFilter#requireEmptyFieldName("user_process")}) mà {@code reply="1"} KHÔNG loại
 * trừ được, nên PHẢI xoá rỗng thêm 7 field điều hướng hàng đợi
 * ({@code user_process}/{@code unit_process}/{@code user_coordinate}/{@code unit_coordinate}/
 * {@code user_to_read}/{@code unit_to_read}/{@code user_track}) — coi như "không còn ai xử lý bản sao
 * này nữa", đúng tinh thần "Lấy lại VB"/"Thu hồi" cục bộ đã làm với văn bản gốc.</p>
 *
 * <p>Ghi thêm 1 dòng {@code vbden_res_documents} ({@code parentid = targetDocumentId}) để văn thư/
 * người đại diện TẠI TENANT ĐÍCH biết vì sao văn bản đột nhiên biến mất khỏi hàng đợi của họ — cùng
 * tinh thần minh bạch đã áp dụng cho {@link VbdenForwardUnitStatusTaskHandler}.</p>
 *
 * <p><b>Best-effort, idempotent</b>: không tìm thấy {@code targetDocumentId} (đã bị xoá/chưa từng tồn
 * tại) → log cảnh báo, KHÔNG fail unit (giống {@link VbdenForwardUnitReplyTaskHandler}) — phía
 * {@code PublishForwardUnitRevokeUseCase} chỉ đánh dấu {@code forwarded_units[].revoked=true} khi
 * OUTBOX publish thành công (không phụ thuộc kết quả xử lý ở ĐÂY), nên chạy lại nhiều lần (redelivery)
 * chỉ ghi đè cùng giá trị / thêm audit trùng — không gây sai lệch dữ liệu nghiêm trọng.</p>
 *
 * <p><b>Phạm vi cascade</b>: xem javadoc {@code com.td.application.vbden.PublishForwardUnitRevokeUseCase}
 * (td-application) — cascade TOÀN BỘ {@code forwarded_units} còn hiệu lực, không lọc thêm theo nhánh
 * tổ chức/người forward.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VbdenForwardUnitRevokeTaskHandler implements TaskHandler {

    private static final String DOCUMENT_TYPE_VBDEN = "vbden";

    private final WorkerProperties props;
    private final TenantDataSourceManager tenantDataSources;
    private final ObjectMapper objectMapper;

    @Override
    public String taskType() {
        return VbdenTaskConstants.TASK_FORWARD_UNIT_REVOKE;
    }

    @Override
    public UnitResult execute(TaskContext context, JsonNode payload) {
        if (!props.getTasks().getVbden().isEnabled()) {
            return UnitResult.failure("worker.tasks.vbden.enabled=false");
        }
        final VbdenForwardUnitRevokePayload p;
        try {
            p = objectMapper.treeToValue(payload, VbdenForwardUnitRevokePayload.class);
        } catch (Exception e) {
            return UnitResult.failure("payload vbden.forward_unit.revoke không hợp lệ: " + e.getMessage());
        }
        if (p == null || p.getTargetDocumentId() == null || p.getTargetDocumentId().isBlank()) {
            return UnitResult.failure("payload vbden.forward_unit.revoke thiếu targetDocumentId");
        }
        UUID targetDocumentId;
        try {
            targetDocumentId = UUID.fromString(p.getTargetDocumentId().trim());
        } catch (Exception e) {
            return UnitResult.failure("targetDocumentId khong hop le: " + p.getTargetDocumentId());
        }

        log.info("vbden.forward_unit.revoke: targetDocumentId={}, reason={}, originDocumentId={} (tenant hien tai {})",
            p.getTargetDocumentId(), p.getReason(), p.getOriginDocumentId(), context.tenantId());

        var dsOpt = tenantDataSources.getDataSource(context.tenantId());
        if (dsOpt.isEmpty()) {
            return UnitResult.failure(
                "Không có connection pool cho tenant " + context.tenantId()
                    + " (kiểm tra control-plane tenants registry / đường mạng tới DB tenant)");
        }
        DataSource ds = dsOpt.get();
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        UUID targetId = targetDocumentId;
        Boolean revokedOne = tx.execute(status -> {
            boolean ok = revokeIncomingDocument(jdbc, targetId);
            if (ok) {
                insertRevokedHistoryRow(jdbc, targetId, p);
            }
            return ok;
        });
        if (!Boolean.TRUE.equals(revokedOne)) {
            log.warn("vbden.forward_unit.revoke: khong tim thay ban sao id={} tai tenant {} (co the da bi xoa)",
                targetDocumentId, context.tenantId());
        }
        return UnitResult.success();
    }

    private boolean revokeIncomingDocument(JdbcTemplate jdbc, UUID targetDocumentId) {
        String deleteDate = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        int updated = jdbc.update("""
                UPDATE documents
                SET dynamic_fields = COALESCE(dynamic_fields, '{}'::jsonb) || jsonb_build_object(
                        'reply', '1', 'is_revoke', '1', 'delete_date', ?::text,
                        'user_process', '[]'::jsonb, 'unit_process', '[]'::jsonb,
                        'user_coordinate', '[]'::jsonb, 'unit_coordinate', '[]'::jsonb,
                        'user_to_read', '[]'::jsonb, 'unit_to_read', '[]'::jsonb,
                        'user_track', '[]'::jsonb)
                WHERE id = ? AND document_type = ?
                """,
            deleteDate, targetDocumentId, DOCUMENT_TYPE_VBDEN);
        return updated > 0;
    }

    private void insertRevokedHistoryRow(JdbcTemplate jdbc, UUID targetDocumentId, VbdenForwardUnitRevokePayload p) {
        String label = "laylaivanban".equals(p.getReason()) ? "Lấy lại VB"
            : "thuhoi".equals(p.getReason()) ? "Thu hồi" : "lấy lại/thu hồi";
        StringBuilder opinion = new StringBuilder("Văn bản đã bị \"").append(label)
            .append("\" bởi đơn vị gốc");
        if (p.getOpinion() != null && !p.getOpinion().isBlank()) {
            opinion.append(": ").append(p.getOpinion());
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("parentid", targetDocumentId.toString());
        fields.put("action", p.getReason());
        fields.put("opinion", opinion.toString());
        String actor = p.getActingUsername() == null || p.getActingUsername().isBlank()
            ? "Hệ thống" : p.getActingUsername();
        fields.put("nguoi_thuc_hien", actor);
        fields.put("creator", actor);
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
                VALUES (?, ?, 'vbden_res', 'DRAFT', '[]', '{}', '{}', ?::jsonb, 1, CURRENT_TIMESTAMP)
                """,
            UUID.randomUUID(), label, dynamicFieldsJson);
    }
}
