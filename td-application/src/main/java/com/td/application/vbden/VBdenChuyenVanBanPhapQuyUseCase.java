package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.email.EmailAttachmentRef;
import com.td.application.common.email.EmailMessage;
import com.td.application.common.email.EmailSender;
import com.td.application.common.models.Result;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AddDynamicDocumentUseCase;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.domain.documents.BusinessDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * N16 (03/09/2026 — bổ sung theo yêu cầu người dùng): tương đương {@code VBdenChuyenVanBanPhapQuy
 * .java} thật (nút "Chuyển VBQP" — {@code VBDenButtonActionVT.jsx#btn_chuyenVBQP}) — chuyển văn bản
 * đến (đã ban hành, có {@code code}/{@code promulgation_date}...) sang hệ thống "văn bản quy phạm
 * pháp luật" bên ngoài.
 *
 * <p><b>Khác biệt CÓ CHỦ ĐÍCH so với Domino thật (ghi rõ, không suy diễn thêm)</b>:</p>
 * <ol>
 *   <li><b>"Gửi" ({@code docVbpq.send(false)})</b> — Domino thật định tuyến document qua cơ chế
 *   "mail-in database" nội bộ Domino (route giữa các NSF trong cùng hạ tầng), KHÔNG phải SMTP/email
 *   thật — ĐÚNG tình trạng đã ghi nhận ở {@link EmailSender} (BL-221, rà soát toàn bộ source Domino
 *   không tìm thấy tích hợp SMTP nào). Vì Java không có hạ tầng "mail-in" tương đương và
 *   {@code mailin} FE gửi lên có dạng địa chỉ (đọc từ {@code cfg_vbpq_mailin}), tái dùng ĐÚNG hạ
 *   tầng {@link EmailSender} đã có cho BL-221 (gửi email tới {@code mailin}, đính kèm toàn bộ file
 *   của văn bản — cùng {@link ListDocumentFilesUseCase} mà {@code GuiEmailVbdiUseCase} dùng).</li>
 *   <li><b>{@code docVbpq} (document mới trong CÙNG database {@code vbden}, Domino thật)</b> — port
 *   thành 1 {@link BusinessDocument} mới, {@code documentType="vbden_vbpq"}, qua Dynamic Data Engine
 *   chung (BL-101) — CÙNG quyết định "tạm thời dùng direct DB" đã áp dụng cho
 *   {@link GuiSangVbcddhUseCase} (module đích chưa có entity/bảng riêng).</li>
 *   <li><b>Không copy file VÀO {@code docVbpq}</b> (Domino: {@code docVbdenFile.copyAllItems}) —
 *   cùng quyết định KHÔNG copy file đã áp dụng ở {@link GuiSangVbcddhUseCase} (xem javadoc lớp đó);
 *   file thay vào đó được đính kèm TRỰC TIẾP vào email gửi đi (mục đích cuối cùng của việc copy file
 *   trong Domino cũng là để "file đi kèm khi gửi", nên đính kèm thẳng vào email đạt cùng mục đích mà
 *   không cần nhân bản file sang document mới).</li>
 *   <li><b>Cấu hình {@code cfg_chuyenvbqp}/{@code cfg_vbpq_mailin}</b> — nút chỉ hiện khi
 *   {@code systemConfig?.data?.cfg_chuyenvbqp === "1"} ({@code VBDenButtonActionVT.jsx}); Java
 *   {@code QLVBCompatAuthController#getCauHinh} hiện đã trả 2 field này (mặc định TẮT/rỗng — xem
 *   javadoc phương thức đó) nhưng CHƯA có màn "Cấu hình hệ thống" (BL-xxx, ngoài phạm vi việc này)
 *   để admin bật/đổi qua UI — cần cấu hình qua {@code application.yml}/biến môi trường.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class VBdenChuyenVanBanPhapQuyUseCase {

    public static final String SOURCE_DOCUMENT_TYPE = "vbden";
    public static final String TARGET_DOCUMENT_TYPE = "vbden_vbpq";

    private static final String NOT_FOUND_MESSAGE = "Khong tim thay van ban";
    private static final Pattern SO_KYHIEU_PATTERN = Pattern.compile("^(\\d+)\\s*[-/]\\s*(.+)$");
    private static final String VBQP_SENT_DATE_FIELD = "vbqp_sent_date";
    private static final String VBQP_SENT_TO_FIELD = "vbqp_sent_to";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DocumentRepository documentRepository;
    private final AddDynamicDocumentUseCase addDynamicDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final ListDocumentFilesUseCase listDocumentFilesUseCase;
    private final EmailSender emailSender;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;

    public Result<UUID> execute(VBdenChuyenVanBanPhapQuyRequest request) {
        var sourceOptional = documentRepository.findById(request.getIdqlvb());
        if (sourceOptional.isEmpty() || sourceOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var source = sourceOptional.get();
        if (!SOURCE_DOCUMENT_TYPE.equalsIgnoreCase(source.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        Map<String, Object> sourceFields = dynamicDocumentFieldsService.parseDynamicFields(source.getDynamicFieldsJson());

        var targetResult = createVbpqDocument(source, sourceFields, request);
        if (!targetResult.isSuccess()) {
            return targetResult;
        }

        var sendResult = sendEmail(source, sourceFields, request.getMailin());
        if (!sendResult.isSuccess()) {
            return Result.failure(sendResult.getError());
        }

        recordVbqpSent(source, sourceFields, request.getMailin());

        return targetResult;
    }

    // Domino: dbVbden.createDocument(), map OutNum/OutSymbol/isuse/OutLinhVuc/OutDate/OutSigner/
    // OutSigner_Pos/OutType/Subject (VBdenChuyenVanBanPhapQuy.java dong 62-79).
    private Result<UUID> createVbpqDocument(
            BusinessDocument source, Map<String, Object> sourceFields, VBdenChuyenVanBanPhapQuyRequest request) {
        var targetRequest = new AddDynamicDocumentRequest();
        targetRequest.setTitle(source.getTitle());
        targetRequest.setDocumentType(TARGET_DOCUMENT_TYPE);

        Map<String, String> targetFields = new LinkedHashMap<>();
        targetFields.put("idqlvb", source.getId().toString());
        String code = asString(sourceFields.get("code"));
        String[] soKyHieu = parseSoKyHieu(code);
        targetFields.put("OutNum", soKyHieu[0]);
        targetFields.put("OutSymbol", soKyHieu[1]);
        putIfPresent(targetFields, "isuse", sourceFields.get("unit_in_charge"));
        putIfPresent(targetFields, "OutLinhVuc", sourceFields.get("sector"));
        putIfPresent(targetFields, "OutDate", sourceFields.get("promulgation_date"));
        putIfPresent(targetFields, "OutSigner", sourceFields.get("signer"));
        putIfPresent(targetFields, "OutSigner_Pos", sourceFields.get("signer_position"));
        putIfPresent(targetFields, "OutType", sourceFields.get("document_type"));
        targetFields.put("Subject", source.getTitle());
        targetRequest.setFields(targetFields);

        return addDynamicDocumentUseCase.execute(targetRequest);
    }

    // Xem muc (1) javadoc lop - thay the "docVbpq.send(false)" (mail-in noi bo Domino, khong phai
    // SMTP that) bang EmailSender da co san cho BL-221, dinh kem toan bo file cua van ban.
    private Result<Void> sendEmail(BusinessDocument source, Map<String, Object> sourceFields, String mailin) {
        List<String> recipients = new ArrayList<>();
        for (String part : mailin.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                recipients.add(trimmed);
            }
        }
        if (recipients.isEmpty()) {
            return Result.failure("Chua khai bao mailin ket noi van ban quy pham!");
        }

        String code = asString(sourceFields.get("code"));
        String subject = source.getTitle() == null ? "" : source.getTitle();
        if (code != null && !code.isBlank()) {
            subject = subject + " (" + code + ")";
        }

        var message = EmailMessage.builder()
            .to(recipients)
            .subject(subject)
            .body(subject)
            .attachments(collectAttachments(source.getId()))
            .build();

        return emailSender.send(message);
    }

    private List<EmailAttachmentRef> collectAttachments(UUID documentId) {
        var filesResponse = listDocumentFilesUseCase.execute(documentId);
        if (filesResponse == null || filesResponse.getItems() == null) {
            return List.of();
        }
        return filesResponse.getItems().stream().map(this::toAttachmentRef).toList();
    }

    private EmailAttachmentRef toAttachmentRef(FileDto file) {
        String name = file.getOriginalFileName() != null ? file.getOriginalFileName() : file.getFileName();
        return new EmailAttachmentRef(file.getFileId(), name);
    }

    private void recordVbqpSent(BusinessDocument source, Map<String, Object> sourceFields, String mailin) {
        var before = buildSnapshot(source);
        Map<String, Object> updatedFields = new LinkedHashMap<>(sourceFields);
        updatedFields.put(VBQP_SENT_DATE_FIELD, LocalDateTime.now().toString());
        updatedFields.put(VBQP_SENT_TO_FIELD, mailin);

        source.replaceDynamicFields(toJson(updatedFields));
        var saved = documentRepository.save(source);
        documentCacheService.evict(saved.getId());
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    // Domino parseSoKyHieu() (VBdenChuyenVanBanPhapQuy.java dong 124-136) - giu nguyen y het regex.
    private String[] parseSoKyHieu(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new String[] {"", ""};
        }
        Matcher matcher = SO_KYHIEU_PATTERN.matcher(input.trim());
        if (matcher.find()) {
            return new String[] {matcher.group(1), matcher.group(2)};
        }
        return new String[] {"", ""};
    }

    private void putIfPresent(Map<String, String> target, String key, Object value) {
        if (value != null && !value.toString().isBlank()) {
            target.put(key, value.toString());
        }
    }

    private Map<String, Object> buildSnapshot(BusinessDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
