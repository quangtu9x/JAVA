package com.td.application.edithistory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.UserFullNameResolver;
import com.td.domain.edithistory.EditHistoryDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 30/10/2026 (cutover qlvb/add): ghi 1 vết hiệu chỉnh vào bảng dùng chung {@code edit_history_documents}
 * — thay side-effect Domino của {@code LogVariable.getChangedFieldDetails} (POST {@code form="log"} lên
 * {@code qlvb/add}, trên tenant Java rơi 404). Mirror {@code AddVbdenEditHistoryUseCase} nhưng KHÔNG guard
 * bản ghi cha (cha nằm ở nhiều bảng/DB: danh mục control-plane, người dùng, đơn vị, ...).
 * {@code userCreator} server-authoritative qua {@link CurrentUserProvider}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddEditHistoryUseCase {

    private static final String FALLBACK_USERNAME = "system";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EditHistoryDocumentRepository editHistoryDocumentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final UserFullNameResolver userFullNameResolver;

    public Result<UUID> execute(AddEditHistoryRequest request) {
        if (request == null || request.getParentId() == null || request.getParentId().isBlank()) {
            return Result.failure("Thiếu id bản ghi cần ghi vết hiệu chỉnh");
        }
        Map<String, Object> editObj = request.getEditObj() == null
            ? new LinkedHashMap<>() : request.getEditObj();
        if (editObj.isEmpty()) {
            return Result.failure("Danh sách thay đổi (edit_obj) trống, không ghi vết");
        }
        String username = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);
        var entity = new EditHistoryDocument(
            request.getParentId().trim(),
            blankToNull(request.getDatabaseType()),
            blankToNull(request.getFormEdit()),
            toJson(editObj),
            userFullNameResolver.resolveForWrite(username, request.getCreatorName()),
            username);
        return Result.success(editHistoryDocumentRepository.save(entity).getId());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("Serialize edit_obj thất bại, lưu {} rỗng", ex.getMessage());
            return "{}";
        }
    }
}
