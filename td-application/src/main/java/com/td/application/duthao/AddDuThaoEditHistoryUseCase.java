package com.td.application.duthao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentLogService;
import com.td.domain.duthao.DuThaoEditHistoryDocument;
import com.td.application.sharedcore.UserFullNameResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Yêu cầu người dùng 27/08/2026 ("onFinish có gọi api {@code qlvb/add[?databasetype=duthao]} (cũ)
 * để lưu vết hiệu chỉnh của dự thảo -> port sang java ... tạo bảng lưu vết riêng"): writer DUY NHẤT
 * của bảng vật lý ĐỘC LẬP {@code duthao_edit_history_documents} (xem
 * {@link DuThaoEditHistoryDocument}) — port phần SIDE-EFFECT "ghi vết field thay đổi" của FE
 * {@code LogVariable.getChangedFieldDetails} (trước đây mỗi lần Lưu có field đổi thì tự POST 1
 * document {@code form="log"} lên Domino {@code qlvb/add}, phía đọc {@code VetHieuChinh.jsx} qua
 * {@code GetRes}).
 *
 * <p>Guard nghiệp vụ: dự thảo cha phải tồn tại trong {@code duthao_documents}
 * ({@code document_type='duthao'}, chưa xoá mềm) — giống guard của {@link LuuDuThaoUseCase};
 * {@code editObj} phải khác rỗng (Domino cũng chỉ sinh vết khi có field đổi).</p>
 *
 * <p>Danh tính người hiệu chỉnh SERVER-AUTHORITATIVE: {@code userCreator} = username đăng nhập qua
 * {@link CurrentUserProvider} (fallback "system"), client KHÔNG tự đặt được; {@code creatorName}
 * nếu được gửi chỉ là tên hiển thị phục vụ render (VetHieuChinh.jsx Ưu tiên record.creator rồi mới
 * rơi về record.user_creator). Người thao tác thật vẫn còn truy vết độc lập ở cột audit
 * {@code created_by}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddDuThaoEditHistoryUseCase {

    public static final String DOCUMENT_TYPE = TaoDuThaoUseCase.DOCUMENT_TYPE;

    static final String DEFAULT_FORM_EDIT = "draft_document";

    private static final String FALLBACK_USERNAME = "system";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản dự thảo";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DuThaoEditHistoryDocumentRepository duThaoEditHistoryDocumentRepository;
    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final UserFullNameResolver userFullNameResolver;

    public Result<UUID> execute(AddDuThaoEditHistoryRequest request) {
        if (request == null || request.getParentId() == null) {
            return Result.failure("Thiếu id dự thảo cần ghi vết hiệu chỉnh");
        }
        var draft = duThaoDocumentRepository.findById(request.getParentId()).orElse(null);
        if (draft == null || !DOCUMENT_TYPE.equalsIgnoreCase(draft.getDocumentType()) || draft.isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        Map<String, Object> editObj = request.getEditObj() == null
            ? new LinkedHashMap<>() : request.getEditObj();
        if (editObj.isEmpty()) {
            // Khớp Domino: chỉ có field THAY ĐỔI mới phát sinh vết.
            return Result.failure("Danh sách thay đổi (edit_obj) trống, không ghi vết");
        }

        String username = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);
        var savedFormEdit = request.getFormEdit() == null || request.getFormEdit().isBlank()
            ? DEFAULT_FORM_EDIT : request.getFormEdit();
        var entity = new DuThaoEditHistoryDocument(
            request.getParentId(),
            savedFormEdit,
            toJson(editObj),
            // 25/09/2026: tên hiển thị do SERVER tra theo tài khoản đăng nhập (xem
            // UserFullNameResolver) — trước đây lấy nguyên creatorName client gửi, nhiều đường ghi vết
            // phụ (gắn tệp, ký số) không gửi nên dòng vết chỉ hiện tài khoản.
            userFullNameResolver.resolveForWrite(username, request.getCreatorName()),
            username);

        var saved = duThaoEditHistoryDocumentRepository.save(entity);
        documentLogService.logCreate(saved.getId(), buildSnapshot(saved, editObj.size()));
        return Result.success(saved.getId());
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("Serialize edit_obj thất bại, lưu {} rỗng", ex.getMessage());
            return "{}";
        }
    }

    private Map<String, Object> buildSnapshot(DuThaoEditHistoryDocument document, int changeCount) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("parentId", document.getParentId());
        snapshot.put("formEdit", document.getFormEdit());
        snapshot.put("userCreator", document.getUserCreator());
        snapshot.put("changeCount", changeCount);
        return snapshot;
    }
}
