package com.td.worker.tasks.guinhanquocgia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.worker.core.task.TaskContext;
import com.td.worker.core.task.TaskHandler;
import com.td.worker.core.task.UnitResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Task {@code guinhanquocgia.send} — module MỚI (bổ sung 09/09/2026), TÁCH BIỆT HOÀN TOÀN khỏi
 * {@code com.td.worker.tasks.vbdi}/{@code com.td.worker.tasks.vbden} hiện có (không sửa gì 2 module
 * đó). Điểm mở rộng đúng cơ chế Extensible Worker đã thiết kế (docs/promt-Extensible-Worker-Architecture.txt):
 * 1 {@code @Component implements TaskHandler} mới, {@link com.td.worker.core.task.TaskHandlerRegistry}
 * tự thu thập, KHÔNG sửa Worker Core.
 *
 * <p>THAM KHẢO (không copy nguyên) kiến trúc/mã nguồn ở {@code docs/td-guinhanquocgia} (project
 * Java/Maven cũ tích hợp Trục liên thông văn bản quốc gia VPCP/VXP, chuẩn eDXML QCVN 102) — xem đầy
 * đủ quyết định thiết kế + việc còn bỏ ngỏ ở {@code docs/16-checklist-tich-hop-gui-nhan-quoc-gia.md}.</p>
 *
 * <p><b>Đây là bản KHUNG SƯỜN (scaffold)</b> — phần "build eDXML từ dữ liệu nghiệp vụ thật" (ánh xạ
 * tổ chức/người ký/chữ ký số) CHƯA làm (việc bỏ ngỏ, xem {@link GuiNhanQuocGiaSendPayload} javadoc):
 * payload hiện giả định ĐÃ có sẵn nội dung {@code .edxml} (base64) do bước khác chuẩn bị.</p>
 *
 * <p>Idempotency qua {@link GuiNhanQuocGiaSendLogStore#tryClaim} (dedup theo {@code unit_id} — cùng
 * nguyên tắc {@code VbdiReplyTaskHandler}: {@code unit_id} sinh mới mỗi lần publish, bảo vệ khỏi
 * REDELIVERY thật, không chặn nhầm hành động lặp lại hợp lệ của người dùng).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuiNhanQuocGiaSendTaskHandler implements TaskHandler {

    private final GuiNhanQuocGiaProperties props;
    private final GuiNhanQuocGiaGatewayClient gatewayClient;
    private final GuiNhanQuocGiaSendLogStore sendLogStore;
    private final ObjectMapper objectMapper;

    @Override
    public String taskType() {
        return GuiNhanQuocGiaTaskConstants.TASK_SEND;
    }

    @Override
    public UnitResult execute(TaskContext context, JsonNode payloadNode) {
        if (!props.isEnabled()) {
            return UnitResult.failure("worker.tasks.guinhanquocgia.enabled=false");
        }

        GuiNhanQuocGiaSendPayload payload;
        try {
            payload = objectMapper.treeToValue(payloadNode, GuiNhanQuocGiaSendPayload.class);
        } catch (Exception e) {
            return UnitResult.failure("payload guinhanquocgia.send không hợp lệ: " + e.getMessage());
        }
        if (payload == null || isBlank(payload.getDocumentId()) || isBlank(payload.getRecipientAgencyCode())) {
            return UnitResult.failure(
                "payload guinhanquocgia.send thiếu field bắt buộc (documentId/recipientAgencyCode)");
        }

        log.info("guinhanquocgia.send: documentId={}, recipientAgencyCode={}, docType={} tại tenant {}",
            payload.getDocumentId(), payload.getRecipientAgencyCode(), payload.getDocType(), context.tenantId());

        String unitId = context.unitId();
        boolean claimed = sendLogStore.tryClaim(unitId, context.tenantId(), payload);
        if (!claimed) {
            // Đã xử lý trước đó (redelivery) — idempotent, không gọi lại gateway.
            return UnitResult.success();
        }

        try {
            GuiNhanQuocGiaSendOutcome outcome = gatewayClient.sendEdoc(payload);
            if (outcome.success()) {
                sendLogStore.markSent(unitId, outcome.centerDocId());
                log.info("guinhanquocgia.send: documentId={} gửi thành công, centerDocId={}",
                    payload.getDocumentId(), outcome.centerDocId());
                return UnitResult.success();
            }
            String error = outcome.errorCode() + ": " + outcome.errorDesc();
            sendLogStore.markFailed(unitId, error);
            return UnitResult.failure("guinhanquocgia.send thất bại: " + error);
        } catch (Exception e) {
            sendLogStore.markFailed(unitId, e.getMessage());
            return UnitResult.failure("guinhanquocgia.send lỗi gateway: " + e.getMessage());
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
