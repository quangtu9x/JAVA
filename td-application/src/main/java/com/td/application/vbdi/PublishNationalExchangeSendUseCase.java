package com.td.application.vbdi;

import com.td.application.common.security.CurrentTenantProvider;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.VbdiDeliveryFileRef;
import com.td.application.messaging.MessageUnitSpec;
import com.td.application.messaging.OutboundMessage;
import com.td.application.messaging.OutboxPublisherService;
import com.td.domain.vbdi.VbdiDocument;
import com.td.domain.vbdi.VbdiSendLogDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gửi văn bản đi tới nơi nhận NGOÀI TỈNH (nhóm {@code external_unit}) qua Trục liên thông văn bản quốc gia —
 * phát ĐÚNG 1 task {@code guinhanquocgia.send} cho CẢ lượt thao tác (mọi nơi nhận ngoài tỉnh của lượt đó trong 1
 * gói eDXML), dịch vụ TD Gateway ({@code tdgateway}) dựng gói + gửi + báo kết quả về qua {@code vbdi.reply}.
 * Xem {@code tdgateway/docs/18-checklist-cutover-va-tich-hop-java-be.md} §2.
 *
 * <ul>
 *   <li><b>Khi nào</b>: được gọi trong {@link SendVbdiDocumentUseCase} SAU khi đã ghi "Nhật ký gửi nhận" cho từng
 *   nơi nhận ngoài tỉnh — tức văn bản đã ISSUED (+ có tệp với lượt gửi đầu), cùng transaction (outbox).</li>
 *   <li><b>Loại nghiệp vụ</b>: gửi/gửi lại/gửi lại toàn bộ → mới (Trục báo trùng thì gateway tự chuyển cập nhật);
 *   gửi bổ sung → cập nhật; văn bản thay thế/thu hồi có văn bản bị thay thế → thay thế; thu hồi/lấy lại → thu hồi.</li>
 *   <li><b>Idempotency</b>: unitId sinh mới mỗi lượt thao tác (mỗi lượt đã có dòng nhật ký riêng); broker gửi lại
 *   cùng unit thì gateway bỏ qua.</li>
 *   <li><b>Không phát được</b> (chưa bật kết nối, thiếu mã định danh điện tử đơn vị phát hành): ghi ngay dòng
 *   "Gửi lỗi" kèm lý do vào nhật ký từng nơi nhận — trước đây task bị treo im lặng WAITING_DEPENDENCY.</li>
 * </ul>
 */
@Service
@Slf4j
public class PublishNationalExchangeSendUseCase {

    static final String TASK_TYPE = "guinhanquocgia.send";
    static final String STATUS_FAILED = "Gửi lỗi";
    private static final String SYSTEM_ACTOR = "Hệ thống";
    private static final String REPLY_SEND_TYPE = "phan_hoi_trang_thai";

    private static final Set<String> REVOKE_TYPES = Set.of(
        VbdiSendType.THU_HOI_VAN_BAN, VbdiSendType.LAY_LAI_VAN_BAN, VbdiSendType.THU_HOI_TOAN_BO);

    private final boolean enabled;
    private final OutboxPublisherService outboxPublisherService;
    private final VbdiSendLogDocumentRepository sendLogRepository;
    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentTenantProvider currentTenantProvider;
    private final CurrentUserProvider currentUserProvider;

    public PublishNationalExchangeSendUseCase(
            @Value("${app.vbdi.national-exchange.enabled:false}") boolean enabled,
            OutboxPublisherService outboxPublisherService,
            VbdiSendLogDocumentRepository sendLogRepository,
            VbdiDocumentRepository vbdiDocumentRepository,
            DynamicDocumentFieldsService dynamicDocumentFieldsService,
            CurrentTenantProvider currentTenantProvider,
            CurrentUserProvider currentUserProvider) {
        this.enabled = enabled;
        this.outboxPublisherService = outboxPublisherService;
        this.sendLogRepository = sendLogRepository;
        this.vbdiDocumentRepository = vbdiDocumentRepository;
        this.dynamicDocumentFieldsService = dynamicDocumentFieldsService;
        this.currentTenantProvider = currentTenantProvider;
        this.currentUserProvider = currentUserProvider;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 1 nơi nhận ngoài tỉnh đã ghi "Nhật ký gửi nhận" trong lượt này. */
    public record ExternalRecipient(UUID sendLogId, String agencyCode, String name) {
    }

    /** @return true nếu đã đưa vào outbox; false nếu đã ghi "Gửi lỗi" (không phát). */
    public boolean execute(VbdiDocument document, String sendType, List<ExternalRecipient> recipients,
                           List<VbdiDeliveryFileRef> files) {
        if (recipients == null || recipients.isEmpty()) {
            return false;
        }
        if (!enabled) {
            recordFailure(document, recipients, "Hệ thống chưa bật kết nối Trục liên thông văn bản quốc gia "
                + "(VBDI_NATIONAL_EXCHANGE_ENABLED) — văn bản CHƯA được gửi tới nơi nhận ngoài tỉnh");
            return false;
        }
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        String senderCode = resolveSenderAgencyCode(fields);
        if (senderCode == null) {
            recordFailure(document, recipients, "Đơn vị phát hành chưa khai báo mã định danh điện tử (identifier) "
                + "để gửi lên Trục liên thông quốc gia");
            return false;
        }
        String docType = docType(sendType, fields);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("documentId", document.getId().toString());
        payload.put("senderAgencyCode", senderCode);
        payload.put("senderOrganName", asString(fields.get("unit_publish")));
        payload.put("senderTenantId", currentTenantProvider.currentTenantId().orElse(null));
        payload.put("docType", docType);
        payload.put("subject", document.getTitle());
        List<Map<String, Object>> recipientList = new ArrayList<>();
        for (ExternalRecipient r : recipients) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("agencyCode", r.agencyCode());
            item.put("name", r.name());
            item.put("sendLogId", r.sendLogId().toString());
            recipientList.add(item);
        }
        payload.put("recipients", recipientList);
        payload.put("document", buildDocument(document, fields, docType, recipients));
        payload.put("files", "revocation".equals(docType) || files == null ? List.of() : files.stream().map(f -> {
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("objectKey", f.getObjectKey());
            file.put("bucketName", f.getBucketName());
            file.put("originalFilename", f.getOriginalFilename());
            file.put("contentType", f.getContentType());
            file.put("size", f.getSize());
            file.put("attachmentGroup", f.getAttachmentGroup());
            return file;
        }).toList());

        String unitId = UUID.randomUUID().toString();
        String tenantId = currentTenantProvider.currentTenantId().orElse(null);
        outboxPublisherService.enqueue("vbdi", document.getId(), new OutboundMessage(TASK_TYPE,
            document.getId().toString(), List.of(new MessageUnitSpec(unitId, tenantId, payload))));
        log.info("Da dua guinhanquocgia.send ({}) cua van ban {} toi {} noi nhan ngoai tinh vao outbox",
            docType, document.getId(), recipients.size());
        return true;
    }

    private Map<String, Object> buildDocument(VbdiDocument document, Map<String, Object> fields, String docType,
                                              List<ExternalRecipient> recipients) {
        String[] code = splitCode(fields);
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("codeNumber", code[0]);
        doc.put("codeNotation", code[1]);
        doc.put("promulgationDate", asString(fields.get("promulgation_date")));
        doc.put("promulgationPlace", asString(fields.get("promulgation_place")));
        doc.put("documentTypeName", asString(fields.get("document_type")));
        doc.put("documentTypeKind", "1".equals(asString(fields.get("document_legal_regulations"))) ? 1 : 2);
        doc.put("subject", document.getTitle());
        doc.put("signerPosition", asString(fields.get("signer_position")));
        doc.put("signerFullName", asString(fields.get("signer")));
        doc.put("dueDate", asString(fields.get("deadline_date")));
        doc.put("priority", priority(asString(fields.get("priority"))));
        doc.put("pageAmount", asInt(fields.get("page_amount")));
        doc.put("promulgationAmount", asInt(fields.get("number_of_copy")));
        doc.put("sphereOfPromulgation", asString(fields.get("sector")));
        doc.put("typerNotation", asString(fields.get("draftor")));
        doc.put("paper", normalize(asString(fields.get("delivery_method"))).contains("giay"));
        doc.put("toPlaces", recipients.stream().map(r -> r.name() == null ? r.agencyCode() : r.name()).toList());
        doc.put("staffDepartment", asString(fields.get("unit_publish")));
        doc.put("staffName", currentUserProvider.currentUsername().orElse(null));
        if ("replacement".equals(docType)) {
            doc.put("replacedDocuments", replacedDocuments(fields));
        }
        return doc;
    }

    /** Văn bản bị thay thế (field {@code replaces_document_id}) — đọc số/ký hiệu/ngày để gateway tính mã eDXML. */
    private List<Map<String, Object>> replacedDocuments(Map<String, Object> fields) {
        String replacedId = asString(fields.get("replaces_document_id"));
        if (replacedId == null) {
            return List.of();
        }
        try {
            return vbdiDocumentRepository.findById(UUID.fromString(replacedId)).map(replaced -> {
                Map<String, Object> replacedFields = dynamicDocumentFieldsService.parseDynamicFields(replaced.getDynamicFieldsJson());
                String[] code = splitCode(replacedFields);
                Map<String, Object> ref = new LinkedHashMap<>();
                ref.put("codeNumber", code[0]);
                ref.put("codeNotation", code[1]);
                ref.put("promulgationDate", asString(replacedFields.get("promulgation_date")));
                return List.of(ref);
            }).orElse(List.of());
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    static String docType(String sendType, Map<String, Object> fields) {
        if (REVOKE_TYPES.contains(sendType)) {
            return "revocation";
        }
        if (VbdiSendType.GUI_BO_SUNG.equals(sendType)) {
            return "update";
        }
        if (VbdiSendType.UNIT_REVOKE.equals(sendType) && asString(fields.get("replaces_document_id")) != null) {
            return "replacement";
        }
        // Gửi/gửi lại/gửi lại toàn bộ, và văn bản thay thế/thu hồi không trỏ văn bản cũ: gói MỚI.
        return "new";
    }

    /**
     * Mã định danh điện tử đơn vị phát hành (OrganId trên Trục): field trên văn bản → tổ chức phát hành →
     * tổ tiên gần nhất có khai báo (đơn vị cấp phòng thường dùng mã của cơ quan).
     */
    private String resolveSenderAgencyCode(Map<String, Object> fields) {
        // `identifier` là mã định danh dùng chung trong hệ thống và trên Trục quốc gia.
        // `identifier_other` phục vụ mục đích khác, tuyệt đối không dùng thay mã OrganId.
        return asString(fields.get("unit_publish_identifier"));
    }

    private void recordFailure(VbdiDocument document, List<ExternalRecipient> recipients, String reason) {
        for (ExternalRecipient r : recipients) {
            sendLogRepository.save(new VbdiSendLogDocument(document.getId(), REPLY_SEND_TYPE,
                VbdiSendRecipientGroup.EXTERNAL_UNIT, r.agencyCode(), r.name(), null, STATUS_FAILED, reason, SYSTEM_ACTOR));
        }
        log.warn("Khong gui van ban {} len Truc quoc gia ({} noi nhan): {}", document.getId(), recipients.size(), reason);
    }

    /** [số, ký hiệu]: ưu tiên document_number + code; không có thì tách code_full ở dấu "/" đầu tiên. */
    static String[] splitCode(Map<String, Object> fields) {
        String number = asString(fields.get("document_number"));
        String notation = asString(fields.get("code"));
        if (number != null && notation != null) {
            return new String[]{number, notation};
        }
        String full = asString(fields.get("code_full"));
        if (full != null && full.contains("/")) {
            int slash = full.indexOf('/');
            return new String[]{full.substring(0, slash).trim(), full.substring(slash + 1).trim()};
        }
        return new String[]{number, notation};
    }

    /** Độ khẩn eDXML: 0 thường, 1 khẩn, 2 thượng khẩn, 3 hoả tốc, 4 hoả tốc hẹn giờ. */
    static int priority(String text) {
        String s = normalize(text);
        if (s.contains("hen gio")) return 4;
        if (s.contains("hoa toc")) return 3;
        if (s.contains("thuong khan")) return 2;
        if (s.contains("khan")) return 1;
        return 0;
    }

    private static Integer asInt(Object value) {
        String s = asString(value);
        if (s == null) {
            return null;
        }
        try {
            return (int) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Chuỗi khác rỗng; số dạng Double "92.0" → "92" (bẫy dynamic field số). */
    static String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            double d = n.doubleValue();
            return d == Math.rint(d) ? String.valueOf((long) d) : n.toString();
        }
        String s = value.toString().trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lowered = value.toLowerCase(Locale.ROOT).replace('đ', 'd');
        return Normalizer.normalize(lowered, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }
}
