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
 * Task {@code vbden.forward_unit.status} — port MỞ RỘNG (không có tương đương Domino 1-1, xem ghi
 * chú bên dưới) của {@code DocumentInProcessUnit.java} — chạy TẠI TENANT GỐC (nơi có văn bản gốc bị
 * "chuyển xử lý" sang đơn vị khác tenant), ghi THÊM 1 dòng {@code vbden_res_documents} mỗi khi đơn vị
 * ĐANG GIỮ bản sao thực hiện 1 hành động xử lý mới trên bản sao đó ({@code ChuyenXuLyVbdenUseCase}
 * publish, xem {@code publishStatusBackToOriginIfForwardedCopy}).
 *
 * <p><b>Bổ sung 08/09/2026 — yêu cầu người dùng: "muốn phản hồi trạng thái 'Đã đến' và các trạng thái
 * như chuyển xử lý... cho đơn vị 'Chuyển xử lý' vào quá trình xử lý"</b>: phần "Đã đến" (1 lần duy
 * nhất, lúc tạo bản sao) đã port riêng ở {@link VbdenForwardUnitReplyTaskHandler#insertArrivalHistoryRow}.
 * Task này port phần CÒN LẠI — "các trạng thái như chuyển xử lý..." — TỔNG QUÁT HOÁ thành: MỌI hành
 * động xử lý tiếp theo trên bản sao (không riêng "chuyển xử lý", dù đó là ví dụ người dùng nêu — bản
 * sao có thể được phân công/phối hợp/chuyển tiếp nhiều lần, kể cả forward tiếp sang tenant thứ 3) đều
 * echo về đây. Domino gốc KHÔNG có cơ chế tương đương (chỉ có reply 1 lần lúc tạo bản sao,
 * {@code SendRecievedDocumentInProcessReplyToSender}) — đây là phần MỞ RỘNG theo yêu cầu người dùng,
 * không phải port thuần từ Domino.</p>
 *
 * <p>Ghi vào {@code vbden_res_documents} với {@code dynamic_fields.parentid = originDocumentId} —
 * ĐÚNG field mà {@code ViewQueryService#queryChildrenByParentId}/{@code QuaTrinhXuLy} dùng để tìm vết
 * xử lý của 1 văn bản, nên dòng này hiện lên CÙNG tab "Quá trình xử lý" như mọi vết xử lý nội bộ khác
 * — người xem không cần biết đây là echo từ tenant khác.</p>
 *
 * <p>Best-effort: publish từ {@code ChuyenXuLyVbdenUseCase} không chặn giao dịch "Chuyển xử lý" chính
 * dù task này lỗi/không tới nơi (outbox pattern — xem javadoc lớp đó); ở CHIỀU NÀY, lỗi ghi
 * {@code vbden_res_documents} (vd văn bản gốc đã bị xoá) chỉ log cảnh báo, KHÔNG retry — dữ liệu
 * "quá trình xử lý" là dữ liệu PHỤ TRỢ hiển thị, không phải nghiệp vụ chính.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VbdenForwardUnitStatusTaskHandler implements TaskHandler {

    private final WorkerProperties props;
    private final TenantDataSourceManager tenantDataSources;
    private final ObjectMapper objectMapper;

    @Override
    public String taskType() {
        return VbdenTaskConstants.TASK_FORWARD_UNIT_STATUS;
    }

    @Override
    public UnitResult execute(TaskContext context, JsonNode payload) {
        if (!props.getTasks().getVbden().isEnabled()) {
            return UnitResult.failure("worker.tasks.vbden.enabled=false");
        }
        final VbdenForwardUnitStatusPayload p;
        try {
            p = objectMapper.treeToValue(payload, VbdenForwardUnitStatusPayload.class);
        } catch (Exception e) {
            return UnitResult.failure("payload vbden.forward_unit.status không hợp lệ: " + e.getMessage());
        }
        if (p == null || p.getOriginDocumentId() == null || p.getOriginDocumentId().isBlank()) {
            return UnitResult.failure("payload vbden.forward_unit.status thiếu originDocumentId");
        }
        UUID originDocumentId;
        try {
            originDocumentId = UUID.fromString(p.getOriginDocumentId().trim());
        } catch (Exception e) {
            return UnitResult.failure("originDocumentId khong hop le: " + p.getOriginDocumentId());
        }

        log.info("vbden.forward_unit.status: originDocumentId={}, action={}, tại đơn vị {} (tenant hiện tại {})",
            p.getOriginDocumentId(), p.getAction(), p.getReportingName(), context.tenantId());

        var dsOpt = tenantDataSources.getDataSource(context.tenantId());
        if (dsOpt.isEmpty()) {
            return UnitResult.failure(
                "Không có connection pool cho tenant " + context.tenantId()
                    + " (kiểm tra control-plane tenants registry / đường mạng tới DB tenant)");
        }
        DataSource ds = dsOpt.get();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        // 23/10/2026: id tat dinh theo unit + ON CONFLICT -> unit chay lai khong nhan doi dong lich su.
        UUID historyId = VbdenTaskConstants.unitScopedId(context, "history");
        tx.executeWithoutResult(status -> insertProcessingHistoryRow(jdbc, historyId, originDocumentId, p));
        return UnitResult.success();
    }

    private void insertProcessingHistoryRow(JdbcTemplate jdbc, UUID historyId, UUID originDocumentId,
                                            VbdenForwardUnitStatusPayload p) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("parentid", originDocumentId.toString());
        fields.put("action", p.getAction());
        fields.put("opinion", buildOpinionText(p));
        String actor = (p.getActingUsername() == null || p.getActingUsername().isBlank())
            ? p.getReportingName() : p.getActingUsername();
        fields.put("nguoi_thuc_hien", actor);
        fields.put("creator", actor);
        if (p.getReportingIdentifier() != null && !p.getReportingIdentifier().isBlank()) {
            fields.put("identifier", p.getReportingIdentifier());
        }
        String dynamicFieldsJson;
        try {
            dynamicFieldsJson = objectMapper.writeValueAsString(fields);
        } catch (Exception ex) {
            dynamicFieldsJson = "{}";
        }
        String title = (p.getActionLabel() == null || p.getActionLabel().isBlank()) ? p.getAction() : p.getActionLabel();
        jdbc.update("""
                INSERT INTO vbden_res_documents
                 (id, title, document_type, status, tags_json, attributes_json, metadata_json,
                  dynamic_fields, version_no, created_on)
                VALUES (?, ?, 'vbden_res', 'DRAFT', '[]', '{}', '{}', ?::jsonb, 1, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO NOTHING
                """,
            historyId, title, dynamicFieldsJson);
    }

    private String buildOpinionText(VbdenForwardUnitStatusPayload p) {
        String label = (p.getActionLabel() == null || p.getActionLabel().isBlank()) ? p.getAction() : p.getActionLabel();
        StringBuilder sb = new StringBuilder(label == null ? "" : label);
        if (p.getReportingName() != null && !p.getReportingName().isBlank()) {
            sb.append(" tại đơn vị ").append(p.getReportingName());
        }
        if (p.getNewAssigneesLabel() != null && !p.getNewAssigneesLabel().isBlank()) {
            sb.append(" (giao cho: ").append(p.getNewAssigneesLabel()).append(")");
        }
        if (p.getOpinion() != null && !p.getOpinion().isBlank()) {
            sb.append(": ").append(p.getOpinion());
        }
        return sb.toString();
    }
}
