package com.td.application.phieutrinhtd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
import com.td.domain.phieutrinhtd.PhieuTrinhTdProcessDocument;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "Trình tham khảo" — gửi phiếu trình xin ý kiến song song tới danh sách người tham khảo (KHÔNG đổi
 * {@code status} chính của phiếu trình — nhánh này độc lập với vòng đời trình duyệt/thông qua, xem
 * {@code docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md} mục 2.3). Tương đương FE
 * {@code PhieuTrinhActionModal.jsx#actionname==="trinhthamkhao"} (bộ code phụ, dùng field {@code
 * trangthai}/log {@code nhatkytrinh}) — port dùng field {@code nguoithamkhaohide}/log {@code quatrinh}
 * (đúng cơ chế của bộ code chính {@code ChiTietPage.jsx}, xem tài liệu 20 mục 2.4) để nhất quán 1 cơ
 * chế log duy nhất trong toàn module.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TrinhThamKhaoPhieuTrinhTdUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy phiếu trình";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter CREATE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    // Xem AuthorAclFieldsService javadoc muc "user_fields dong" - "nguoithamkhaohide" ket thuc "hide".
    private static final java.util.Set<String> AUTHOR_TOUCHED_FIELDS = java.util.Set.of("nguoithamkhaohide");

    private final PhieuTrinhTdDocumentRepository phieuTrinhTdDocumentRepository;
    private final PhieuTrinhTdProcessDocumentRepository phieuTrinhTdProcessDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(TrinhThamKhaoPhieuTrinhTdRequest request) {
        if (request == null || request.getId() == null) {
            return Result.failure("Thiếu id phiếu trình");
        }
        PhieuTrinhTdDocument document = phieuTrinhTdDocumentRepository.findById(request.getId()).orElse(null);
        if (document == null || document.isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String username = currentUserProvider.currentUsername().orElse("");
        String fullName = appUserRepository.findByUsernameAndDeletedOnIsNull(username)
            .map(AppUser::getFullName).orElse(username);
        String createDate = java.time.LocalDateTime.now().format(CREATE_DATE_FORMAT);

        Map<String, Object> logFields = new LinkedHashMap<>();
        logFields.put("form", "quatrinh");
        logFields.put("action_type", PhieuTrinhTdDocument.STATUS_TRINH_THAM_KHAO);
        logFields.put("creator", fullName);
        logFields.put("user_creator", username);
        logFields.put("receiver", request.getNguoiThamKhao() == null ? "" : request.getNguoiThamKhao());
        logFields.put("user_receiver", request.getNguoiThamKhaoHide() == null ? "" : request.getNguoiThamKhaoHide());
        logFields.put("opinion", request.getOpinion() == null ? "" : request.getOpinion());
        logFields.put("create_date", createDate);
        logFields.put("parentid", document.getId().toString());

        var processDocument = new PhieuTrinhTdProcessDocument("Trình tham khảo " + document.getId())
            .replaceDynamicFields(toJson(logFields));
        phieuTrinhTdProcessDocumentRepository.save(processDocument);

        Map<String, Object> parentFields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        parentFields.put("nguoithamkhao", mergeList(asString(parentFields.get("nguoithamkhao")), request.getNguoiThamKhao()));
        parentFields.put("nguoithamkhaohide",
            mergeList(asString(parentFields.get("nguoithamkhaohide")), request.getNguoiThamKhaoHide()));
        authorAclFieldsService.applyOnUpdate(PhieuTrinhTdDocument.DOCUMENT_TYPE, parentFields, AUTHOR_TOUCHED_FIELDS);
        document.replaceDynamicFields(toJson(parentFields));

        var saved = phieuTrinhTdDocumentRepository.save(document);
        return Result.success(saved.getId());
    }

    /** Nối thêm danh sách mới vào danh sách hiện có, tránh trùng lặp, đúng định dạng {@code ";"}-nối. */
    private String mergeList(String existing, String additional) {
        if (additional == null || additional.isBlank()) {
            return existing == null ? "" : existing;
        }
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        if (existing != null && !existing.isBlank()) {
            for (String part : existing.split(";")) {
                if (!part.isBlank()) {
                    merged.add(part.trim());
                }
            }
        }
        for (String part : additional.split(";")) {
            if (!part.isBlank()) {
                merged.add(part.trim());
            }
        }
        return String.join(";", merged);
    }

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
