package com.td.application.vbdi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentLogService;
import com.td.domain.vbdi.VbdiEditHistoryDocument;
import com.td.application.sharedcore.UserFullNameResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026 ("ghi log chỉnh sửa thì tạo ra bảng riêng tham khảo thêm như dự
 * thảo đã làm"): writer DUY NHẤT của bảng vật lý ĐỘC LẬP {@code vbdi_edit_history_documents} (xem
 * {@link VbdiEditHistoryDocument}) — mirror {@code AddDuThaoEditHistoryUseCase} — port phần
 * SIDE-EFFECT "ghi vết field thay đổi" của FE {@code LogVariable.getChangedFieldDetails}, CỘNG
 * THÊM được gọi từ {@link VbdiEditHistorySync} cho vết thêm/xoá tệp đính kèm (yêu cầu rõ ràng của
 * người dùng lần này, chưa có tiền lệ ở duthao).
 *
 * <p>Guard nghiệp vụ: văn bản đi cha phải tồn tại trong {@code vbdi_documents}
 * ({@code document_type='vbdi'}, chưa xoá mềm) — giống guard của {@link HieuChinhVbdiUseCase};
 * {@code editObj} phải khác rỗng (Domino cũng chỉ sinh vết khi có thay đổi).</p>
 *
 * <p>Danh tính người hiệu chỉnh SERVER-AUTHORITATIVE: {@code userCreator} = username đăng nhập qua
 * {@link CurrentUserProvider} (fallback "system"), client KHÔNG tự đặt được.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddVbdiEditHistoryUseCase {

    static final String DEFAULT_FORM_EDIT = "vbdi";

    private static final String FALLBACK_USERNAME = "system";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đi";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbdiEditHistoryDocumentRepository vbdiEditHistoryDocumentRepository;
    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final UserFullNameResolver userFullNameResolver;

    public Result<UUID> execute(AddVbdiEditHistoryRequest request) {
        if (request == null || request.getParentId() == null) {
            return Result.failure("Thiếu id văn bản đi cần ghi vết hiệu chỉnh");
        }
        var document = vbdiDocumentRepository.findById(request.getParentId()).orElse(null);
        if (document == null || !SoanThaoVbdiUseCase.DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())
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
        var entity = new VbdiEditHistoryDocument(
            request.getParentId(),
            savedFormEdit,
            toJson(editObj),
            // 25/09/2026: tên hiển thị do SERVER tra theo tài khoản đăng nhập (xem
            // UserFullNameResolver) — trước đây lấy nguyên creatorName client gửi, nhiều đường ghi vết
            // phụ (gắn tệp, ký số) không gửi nên dòng vết chỉ hiện tài khoản.
            userFullNameResolver.resolveForWrite(username, request.getCreatorName()),
            username);

        var saved = vbdiEditHistoryDocumentRepository.save(entity);
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

    private Map<String, Object> buildSnapshot(VbdiEditHistoryDocument document, int changeCount) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("parentId", document.getParentId());
        snapshot.put("formEdit", document.getFormEdit());
        snapshot.put("userCreator", document.getUserCreator());
        snapshot.put("changeCount", changeCount);
        return snapshot;
    }
}
