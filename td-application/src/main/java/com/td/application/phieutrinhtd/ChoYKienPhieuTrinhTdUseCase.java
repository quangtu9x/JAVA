package com.td.application.phieutrinhtd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
import com.td.domain.phieutrinhtd.PhieuTrinhTdProcessDocument;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 06/10/2026 (cutover FE phiếu trình - thay {@code qlvb/add?databasetype=phieutrinhtd&parentid=}
 * form {@code document_coordinated_opinion}, {@code ActionModal.jsx#btnActionPhoiHop}): người phối hợp
 * cho ý kiến góp ý.
 * <ol>
 *   <li>Tạo dòng ý kiến (bảng quá trình, {@code form=document_coordinated_opinion}, {@code parentid} +
 *   {@code submission_dossier_id} = id phiếu trình - view {@code vwCoordinated_Opinion_Submission_Dossier_ID}).</li>
 *   <li>Thêm người cho ý kiến vào {@code user_coordinated}/{@code coordinated} của phiếu trình - Domino làm
 *   việc này trong API lưu chung (chú thích FE "đang thêm tạm trong API lưu chung"); nhờ đó phiếu trình
 *   rời khỏi "Chờ cho ý kiến" và vào "Đã cho ý kiến".</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ChoYKienPhieuTrinhTdUseCase {

    static final String FORM_OPINION = "document_coordinated_opinion";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter CREATE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final PhieuTrinhTdDocumentRepository phieuTrinhTdDocumentRepository;
    private final PhieuTrinhTdProcessDocumentRepository phieuTrinhTdProcessDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    public Result<ChuyenTrangThaiPhieuTrinhTdResult> execute(ChoYKienPhieuTrinhTdRequest request) {
        if (request == null || request.getId() == null) {
            return Result.failure("Thiếu id phiếu trình");
        }
        PhieuTrinhTdDocument document = phieuTrinhTdDocumentRepository.findById(request.getId()).orElse(null);
        if (document == null || document.isDeleted()) {
            return Result.failure("Không tìm thấy phiếu trình");
        }
        String username = currentUserProvider.currentUsername().orElse("");
        if (username.isBlank()) {
            return Result.failure("Không xác định được người dùng hiện tại");
        }
        String fullName = appUserRepository.findByUsernameAndDeletedOnIsNull(username)
            .map(AppUser::getFullName).orElse(username);

        Map<String, Object> opinion = new LinkedHashMap<>();
        opinion.put("form", FORM_OPINION);
        opinion.put("parentid", document.getId().toString());
        opinion.put("submission_dossier_id", document.getId().toString());
        opinion.put("subject", request.getSubject() == null ? "" : request.getSubject());
        opinion.put("creator", fullName);
        opinion.put("user_creator", username);
        opinion.put("create_date", LocalDateTime.now().format(CREATE_DATE_FORMAT));
        var savedOpinion = phieuTrinhTdProcessDocumentRepository.save(
            new PhieuTrinhTdProcessDocument("Ý kiến góp ý phiếu trình " + document.getId())
                .replaceDynamicFields(toJson(opinion)));

        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        fields.put("user_coordinated", addMember(asString(fields.get("user_coordinated")), username));
        fields.put("coordinated", addMember(asString(fields.get("coordinated")), fullName));
        document.replaceDynamicFields(toJson(fields));
        phieuTrinhTdDocumentRepository.save(document);

        return Result.success(new ChuyenTrangThaiPhieuTrinhTdResult(document.getId(), savedOpinion.getId()));
    }

    // Khuôn ";a;b;" FE đang dùng cho danh sách người (ActionModal#addUserToFormData_User_Name).
    static String addMember(String list, String member) {
        String current = list == null ? "" : list.trim();
        String wrapped = ";" + current.replace(",", ";") + ";";
        if (wrapped.toLowerCase().contains(";" + member.toLowerCase() + ";")) {
            return current;
        }
        String joined = (current.isEmpty() ? ";" : current + (current.endsWith(";") ? "" : ";")) + member + ";";
        return joined.startsWith(";") ? joined : ";" + joined;
    }

    private static String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
