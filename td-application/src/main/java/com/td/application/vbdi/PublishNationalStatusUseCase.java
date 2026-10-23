package com.td.application.vbdi;

import com.td.application.common.security.CurrentTenantProvider;
import com.td.application.messaging.MessageUnitSpec;
import com.td.application.messaging.OutboundMessage;
import com.td.application.messaging.OutboxPublisherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phản hồi trạng thái xử lý văn bản ĐẾN TỪ TRỤC QUỐC GIA (vào sổ 03, phân công 04, đang xử lý 05, hoàn thành 06, từ chối
 * 02/trả lại) về đơn vị gửi ngoài tỉnh — phát {@code guinhanquocgia.status.send}; TD Gateway dịch mã nội bộ → mã eDXML
 * ({@code GuiNhanQuocGiaStatusCodes}) và dựng gói trạng thái. Xem {@code tdgateway/docs/21-goi-trang-thai.md}.
 *
 * <p>Nhận diện văn bản đến từ Trục: {@code send_log_id} bắt đầu {@value #NATIONAL_PREFIX} (TD Gateway gắn khi bàn giao).
 * Được gọi từ {@link PublishVbdiReplyStatusUseCase} — cùng điểm móc với phản hồi trong tỉnh, nên mọi nghiệp vụ đã phát
 * phản hồi (LuuVanBanDen, ChuyenXuLy, KetThucXuLy, TuChoiTiepNhan, CapNhatVanBanThayTheThuHoi) tự liên thông ra Trục.</p>
 */
@Service
@Slf4j
public class PublishNationalStatusUseCase {

    static final String NATIONAL_PREFIX = "national:";
    static final String TASK_TYPE = "guinhanquocgia.status.send";

    private final boolean enabled;
    private final OutboxPublisherService outboxPublisherService;
    private final CurrentTenantProvider currentTenantProvider;

    public PublishNationalStatusUseCase(@Value("${app.vbdi.national-exchange.enabled:false}") boolean enabled,
                                        OutboxPublisherService outboxPublisherService,
                                        CurrentTenantProvider currentTenantProvider) {
        this.enabled = enabled;
        this.outboxPublisherService = outboxPublisherService;
        this.currentTenantProvider = currentTenantProvider;
    }

    public static boolean isNationalIncoming(Map<String, Object> fields) {
        Object sendLogId = fields == null ? null : fields.get("send_log_id");
        return sendLogId != null && String.valueOf(sendLogId).startsWith(NATIONAL_PREFIX);
    }

    /** @return true nếu đã đưa vào outbox. */
    public boolean execute(PublishVbdiReplyStatusRequest request) {
        Map<String, Object> fields = request.getDynamicFields();
        if (!isNationalIncoming(fields)) {
            return false;
        }
        if (!enabled) {
            log.info("Chua bat ket noi Truc quoc gia (VBDI_NATIONAL_EXCHANGE_ENABLED) - khong phan hoi trang thai {} "
                + "cho van ban den {}", request.getStatusCode(), fields.get("send_log_id"));
            return false;
        }
        String senderOrgan = asString(fields.get("sender_identifier"));
        String ourOrgan = asString(fields.get("contact_unit_identifier"));
        if (senderOrgan == null || ourOrgan == null) {
            log.warn("Van ban den tu Truc {} thieu sender_identifier/contact_unit_identifier - khong phan hoi trang thai {}",
                fields.get("send_log_id"), request.getStatusCode());
            return false;
        }

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("statusCode", request.getStatusCode());
        status.put("description", firstNonBlank(request.getStatusDetails(), request.getStatusLabel()));
        status.put("staffName", request.getActorDisplayName());
        status.put("responseForOrganId", senderOrgan);
        status.put("responseForCode", asString(fields.get("code")));
        status.put("responseForPromulgationDate", asString(fields.get("promulgation_date")));
        status.put("responseForDocumentId", asString(fields.get("national_document_id")));

        Map<String, Object> payload = new LinkedHashMap<>();
        String transportId = asString(fields.get("parentid_send"));
        payload.put("documentId", transportId == null ? String.valueOf(fields.get("send_log_id")) : transportId);
        payload.put("senderAgencyCode", ourOrgan);
        payload.put("recipientAgencyCode", senderOrgan);
        payload.put("status", status);

        String tenantId = currentTenantProvider.currentTenantId().orElse(null);
        outboxPublisherService.enqueue("vbdi_reply", UUID.randomUUID(), new OutboundMessage(TASK_TYPE,
            String.valueOf(payload.get("documentId")),
            List.of(new MessageUnitSpec(UUID.randomUUID().toString(), tenantId, payload))));
        log.info("Da dua phan hoi trang thai {} (van ban den tu Truc {}) vao outbox", request.getStatusCode(), transportId);
        return true;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }
}
