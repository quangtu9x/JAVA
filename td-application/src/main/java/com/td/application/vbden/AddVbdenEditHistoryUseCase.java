package com.td.application.vbden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.domain.vbden.VbdenEditHistoryDocument;
import com.td.application.sharedcore.UserFullNameResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Yêu cầu người dùng 12/09/2026 ("văn bản đến chưa thấy hiển thị vết hiệu chỉnh, làm như văn bản
 * đi"): writer DUY NHẤT của bảng vật lý ĐỘC LẬP {@code vbden_edit_history_documents} (xem
 * {@link VbdenEditHistoryDocument}) — mirror {@code AddVbdiEditHistoryUseCase} — port phần
 * SIDE-EFFECT "ghi vết field thay đổi" của FE {@code LogVariable.getChangedFieldDetails}.
 *
 * <p>Guard nghiệp vụ: văn bản đến cha phải tồn tại trong bảng {@code documents} chung
 * ({@code document_type='vbden'}, chưa xoá mềm — dùng {@link DocumentRepository} generic, ĐÚNG như
 * {@link LuuVanBanDenUseCase} đang guard, KHÁC vbdi có bảng/entity riêng); {@code editObj} phải khác
 * rỗng (Domino cũng chỉ sinh vết khi có thay đổi).</p>
 *
 * <p>Danh tính người hiệu chỉnh SERVER-AUTHORITATIVE: {@code userCreator} = username đăng nhập qua
 * {@link CurrentUserProvider} (fallback "system"), client KHÔNG tự đặt được.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddVbdenEditHistoryUseCase {

    static final String DEFAULT_FORM_EDIT = TiepNhanVbdenUseCase.DOCUMENT_TYPE;

    private static final String FALLBACK_USERNAME = "system";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đến";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbdenEditHistoryDocumentRepository vbdenEditHistoryDocumentRepository;
    private final DocumentRepository documentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final UserFullNameResolver userFullNameResolver;

    public Result<UUID> execute(AddVbdenEditHistoryRequest request) {
        if (request == null || request.getParentId() == null) {
            return Result.failure("Thiếu id văn bản đến cần ghi vết hiệu chỉnh");
        }
        var document = documentRepository.findById(request.getParentId()).orElse(null);
        if (document == null || !DEFAULT_FORM_EDIT.equalsIgnoreCase(document.getDocumentType())
                || document.isDeleted()) {
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
        var entity = new VbdenEditHistoryDocument(
            request.getParentId(),
            savedFormEdit,
            toJson(editObj),
            // 25/09/2026: tên hiển thị do SERVER tra theo tài khoản đăng nhập (xem
            // UserFullNameResolver) — trước đây lấy nguyên creatorName client gửi, nhiều đường ghi vết
            // phụ (gắn tệp, ký số) không gửi nên dòng vết chỉ hiện tài khoản.
            userFullNameResolver.resolveForWrite(username, request.getCreatorName()),
            username);

        var saved = vbdenEditHistoryDocumentRepository.save(entity);
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

    private Map<String, Object> buildSnapshot(VbdenEditHistoryDocument document, int changeCount) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("parentId", document.getParentId());
        snapshot.put("formEdit", document.getFormEdit());
        snapshot.put("userCreator", document.getUserCreator());
        snapshot.put("changeCount", changeCount);
        return snapshot;
    }
}
