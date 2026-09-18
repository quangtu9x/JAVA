package com.td.application.vbdi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.email.EmailAttachmentRef;
import com.td.application.common.email.EmailMessage;
import com.td.application.common.email.EmailSender;
import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.domain.vbdi.VbdiDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BL-221 (VBDi — Ký số & Gửi đi) — DoD (3): gửi văn bản đi qua email công vụ. Chỉ cho phép sau khi
 * đã ban hành ({@link VbdiStatus#ISSUED}) — đúng thứ tự nghiệp vụ tự nhiên (soạn thảo → trình ký →
 * ban hành → gửi đi), cùng cách BL-220 dùng status guard cho {@code BanHanhVbdiUseCase}. Đính kèm
 * toàn bộ file đã upload của văn bản (qua {@link ListDocumentFilesUseCase}, không chỉ file chính).
 *
 * <p>Xem Javadoc {@link EmailSender} về giới hạn bằng chứng nguồn (không tìm thấy SMTP thật trong
 * Domino, chỉ có cơ chế "mail-in" định tuyến nội bộ khác bản chất).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GuiEmailVbdiUseCase {

    public static final String DOCUMENT_TYPE = "vbdi";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đi";
    private static final String NOT_ISSUED_MESSAGE = "Văn bản chưa ban hành, không thể gửi email";
    private static final String EMAIL_SENT_DATE_FIELD = "email_sent_date";
    private static final String EMAIL_SENT_TO_FIELD = "email_sent_to";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final ListDocumentFilesUseCase listDocumentFilesUseCase;
    private final EmailSender emailSender;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;

    public Result<Void> execute(GuiEmailVbdiRequest request) {
        var documentOptional = vbdiDocumentRepository.findById(request.getId());
        if (documentOptional.isEmpty() || documentOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var document = documentOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        if (!VbdiStatus.ISSUED.equals(document.getStatus())) {
            return Result.failure(NOT_ISSUED_MESSAGE);
        }

        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        String subject = (request.getSubject() == null || request.getSubject().isBlank())
            ? buildDefaultSubject(document, fields)
            : request.getSubject();

        var message = EmailMessage.builder()
            .to(request.getToEmails())
            .subject(subject)
            .body(request.getMessage())
            .attachments(collectAttachments(request.getId()))
            .build();

        var sendResult = emailSender.send(message);
        if (!sendResult.isSuccess()) {
            return sendResult;
        }

        recordEmailSent(document, fields, request.getToEmails());

        return sendResult;
    }

    private String buildDefaultSubject(VbdiDocument document, Map<String, Object> fields) {
        Object documentNumber = fields.get("document_number");
        String title = document.getTitle() == null ? "" : document.getTitle();
        return documentNumber == null ? title : title + " (" + documentNumber + ")";
    }

    private List<EmailAttachmentRef> collectAttachments(java.util.UUID documentId) {
        var filesResponse = listDocumentFilesUseCase.execute(documentId);
        if (filesResponse == null || filesResponse.getItems() == null) {
            return List.of();
        }
        return filesResponse.getItems().stream()
            .map(this::toAttachmentRef)
            .toList();
    }

    private EmailAttachmentRef toAttachmentRef(FileDto file) {
        String name = file.getOriginalFileName() != null ? file.getOriginalFileName() : file.getFileName();
        return new EmailAttachmentRef(file.getFileId(), name);
    }

    private void recordEmailSent(VbdiDocument document, Map<String, Object> fields, List<String> toEmails) {
        var before = buildSnapshot(document);
        Map<String, Object> updatedFields = new LinkedHashMap<>(fields);
        updatedFields.put(EMAIL_SENT_DATE_FIELD, LocalDateTime.now().toString());
        updatedFields.put(EMAIL_SENT_TO_FIELD, String.join(";", toEmails));

        document.replaceDynamicFields(toJson(updatedFields));
        var saved = vbdiDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    private Map<String, Object> buildSnapshot(VbdiDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
