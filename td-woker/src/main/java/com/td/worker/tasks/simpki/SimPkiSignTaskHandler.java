package com.td.worker.tasks.simpki;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.worker.config.WorkerProperties;
import com.td.worker.core.task.TaskContext;
import com.td.worker.core.task.TaskHandler;
import com.td.worker.core.task.UnitResult;
import com.td.worker.transport.rabbit.RabbitPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Task {@code simpki.sign} — module MỚI (com.td.worker.tasks.simpki), TÁCH BIỆT HOÀN TOÀN khỏi
 * {@code vbdi}/{@code vbden}/{@code guinhanquocgia} hiện có (không sửa gì các module đó). Điểm mở
 * rộng đúng cơ chế Extensible Worker (docs/promt-Extensible-Worker-Architecture.txt,
 * docs/03-task-handler-model.md): 1 {@code @Component implements TaskHandler} mới,
 * {@link com.td.worker.core.task.TaskHandlerRegistry} tự thu thập, KHÔNG sửa Worker Core.
 *
 * <p>Ký số qua SIM-PKI (MSSP/ETSI TS 102 204, {@code docs/TDSimSign/sdk-mobilepki}) là quy trình
 * ĐỒNG BỘ (chờ người dùng xác nhận trên điện thoại, tối đa {@code worker.tasks.simpki.timeout-seconds})
 * — nên vẫn chạy trong 1 unit của Worker (cơ chế unit-timeout/dispatch hiện có xử lý đúng như bất kỳ
 * lời gọi ngoài chậm nào khác, không cần cơ chế mới).</p>
 *
 * <p>Idempotency qua {@link SimPkiSignLogStore#tryClaim} (dedup theo {@code unit_id} — cùng nguyên
 * tắc {@code GuiNhanQuocGiaSendTaskHandler}/{@code VbdiReplyTaskHandler}: {@code unit_id} sinh mới
 * mỗi lần publish, bảo vệ khỏi REDELIVERY thật). Kết quả (chữ ký/transId/mã xác thực) publish qua
 * {@code simpki.sign_reply} khi {@code worker.rabbit.enabled=true} (cùng pattern
 * {@code VbdiDeliveryTaskHandler#publishArrivedReply}) — Worker KHÔNG tự gắn chữ ký vào file_metadata
 * (đó là dữ liệu nghiệp vụ của td-web, Worker không sở hữu schema tenant ngoài phạm vi dedup).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimPkiSignTaskHandler implements TaskHandler {

    private final SimPkiProperties props;
    private final WorkerProperties workerProps;
    private final SimPkiGatewayClient gatewayClient;
    private final SimPkiSignLogStore signLogStore;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<RabbitPublisher> rabbitPublisher;

    @Override
    public String taskType() {
        return SimPkiTaskConstants.TASK_SIGN;
    }

    @Override
    public UnitResult execute(TaskContext context, JsonNode payloadNode) {
        if (!props.isEnabled()) {
            return UnitResult.failure("worker.tasks.simpki.enabled=false");
        }

        SimPkiSignPayload payload;
        try {
            payload = objectMapper.treeToValue(payloadNode, SimPkiSignPayload.class);
        } catch (Exception e) {
            return UnitResult.failure("payload simpki.sign không hợp lệ: " + e.getMessage());
        }
        if (payload == null || isBlank(payload.getMsisdn()) || isBlank(payload.getHashBase64())) {
            return UnitResult.failure("payload simpki.sign thiếu field bắt buộc (msisdn/hashBase64)");
        }

        log.info("simpki.sign: documentId={}, fileId={}, msisdn={} tại tenant {}",
            payload.getDocumentId(), payload.getFileId(), maskMsisdn(payload.getMsisdn()), context.tenantId());

        String unitId = context.unitId();
        boolean claimed = signLogStore.tryClaim(unitId, context.tenantId(), payload);
        if (!claimed) {
            // Đã xử lý trước đó (redelivery) — idempotent, không gọi lại gateway/không ký lại (ký
            // lại sẽ tạo giao dịch xác nhận MỚI trên điện thoại người dùng, không mong muốn).
            return UnitResult.success();
        }

        try {
            SimPkiSignOutcome outcome = gatewayClient.sign(payload);
            if (outcome.success()) {
                signLogStore.markSigned(unitId, outcome.transId(), outcome.verificationCode(), outcome.statusCode());
                log.info("simpki.sign: documentId={} ký thành công, transId={}, verificationCode={}",
                    payload.getDocumentId(), outcome.transId(), outcome.verificationCode());
                publishSignReply(context, payload, outcome);
                return UnitResult.success(signDetail(outcome));
            }
            String error = outcome.errorCode() + ": " + outcome.errorDesc();
            log.warn("simpki.sign: documentId={}, unitId={}, msisdn={} - MSSP trả lỗi: {}",
                payload.getDocumentId(), unitId, maskMsisdn(payload.getMsisdn()), error);
            signLogStore.markFailed(unitId, error);
            publishSignReply(context, payload, outcome);
            return UnitResult.failure("simpki.sign thất bại: " + error, signDetail(outcome));
        } catch (Exception e) {
            // Trước đây chỉ log qua UnitDispatcher (WARN, e.getMessage() rút gọn, KHÔNG có
            // stack trace) — với lỗi mạng/SDK (vd SSLHandshakeException, AxisFault không có
            // message rõ) rất khó chẩn đoán. Log ERROR kèm stack trace đầy đủ NGAY tại nguồn.
            log.error("simpki.sign: documentId={}, unitId={}, msisdn={} - lỗi gọi gateway ({}): {}",
                payload.getDocumentId(), unitId, maskMsisdn(payload.getMsisdn()),
                e.getClass().getSimpleName(), e.getMessage(), e);
            signLogStore.markFailed(unitId, e.getMessage());
            return UnitResult.failure("simpki.sign lỗi gateway: " + e.getMessage());
        }
    }

    /**
     * Payload chi tiết trả ra ngoài Worker Core qua {@link UnitResult#detail()} (xem
     * {@code com.td.worker.core.flow.UnitOutcome}) — CÙNG bộ field với {@link #publishSignReply}
     * gửi qua RabbitMQ, để HTTP debug endpoint (test độc lập không cần broker) thấy được ĐÚNG dữ
     * liệu MSSP trả về thay vì chỉ {@code type/canAck} chung chung của cả message.
     */
    private Map<String, Object> signDetail(SimPkiSignOutcome outcome) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("success", outcome.success());
        if (outcome.success()) {
            detail.put("signatureBase64", outcome.signatureBase64());
            detail.put("transId", outcome.transId());
            detail.put("verificationCode", outcome.verificationCode());
            detail.put("statusCode", outcome.statusCode());
        } else {
            detail.put("errorCode", outcome.errorCode());
            detail.put("errorDesc", outcome.errorDesc());
        }
        return detail;
    }

    /**
     * Publish kết quả ký về producer gốc (td-web/td-application) qua RabbitMQ — cùng pattern
     * {@code VbdiDeliveryTaskHandler#publishArrivedReply} (dựng đủ {@code MessageEnvelope} khớp
     * {@code EnvelopeParser}, routing key {@code "worker." + taskType.replace('.', '_')}). Publish
     * CẢ khi FAILED (để producer biết ký thất bại, không phải "im lặng mất tích") — CHỈ bỏ qua khi
     * RabbitMQ không bật (đúng hành vi hiện có, đơn giản hoá cho môi trường không cần phản hồi bất
     * đồng bộ, vd caller đồng bộ chờ qua cơ chế khác).
     */
    private void publishSignReply(TaskContext context, SimPkiSignPayload payload, SimPkiSignOutcome outcome) {
        if (!workerProps.getRabbit().isEnabled()) {
            return;
        }
        try {
            Map<String, Object> reply = new LinkedHashMap<>();
            reply.put("documentId", payload.getDocumentId());
            reply.put("fileId", payload.getFileId());
            reply.put("success", outcome.success());
            if (outcome.success()) {
                reply.put("signatureBase64", outcome.signatureBase64());
                reply.put("transId", outcome.transId());
                reply.put("verificationCode", outcome.verificationCode());
                reply.put("statusCode", outcome.statusCode());
            } else {
                reply.put("errorCode", outcome.errorCode());
                reply.put("errorDesc", outcome.errorDesc());
            }

            String replyTaskType = isBlank(props.getReplyTaskType())
                ? SimPkiTaskConstants.TASK_SIGN_REPLY_DEFAULT : props.getReplyTaskType();
            String messageId = UUID.randomUUID().toString();
            Map<String, Object> unit = new LinkedHashMap<>();
            unit.put("unitId", context.unitId());
            unit.put("tenantId", context.tenantId());
            unit.put("payload", reply);
            unit.put("target", Map.of());

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("messageId", messageId);
            envelope.put("taskType", replyTaskType);
            envelope.put("correlationId", context.correlationId() == null ? "" : context.correlationId());
            envelope.put("source", "td-worker-simpki-sign");
            envelope.put("createdAt", Instant.now().toString());
            envelope.put("headers", Map.of());
            envelope.put("units", List.of(unit));

            String json = objectMapper.writeValueAsString(envelope);
            String routingKey = "worker." + replyTaskType.replace('.', '_');
            rabbitPublisher.ifAvailable(rp -> rp.publish(workerProps.getRabbit().getExchange(), routingKey, json));
            log.info("simpki.sign: đã publish phản hồi (unitId={}) qua routing {}", context.unitId(), routingKey);
        } catch (Exception e) {
            // Phản hồi không gửi được KHÔNG làm FAIL unit gốc (đã ký/đã audit xong; phản hồi gửi lại
            // được sau qua cơ chế khác — cùng nguyên tắc VbdiDeliveryTaskHandler).
            log.warn("simpki.sign: publish phản hồi thất bại: {}", e.getMessage());
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String maskMsisdn(String msisdn) {
        if (msisdn == null || msisdn.length() < 4) {
            return "***";
        }
        return "***" + msisdn.substring(msisdn.length() - 3);
    }
}
