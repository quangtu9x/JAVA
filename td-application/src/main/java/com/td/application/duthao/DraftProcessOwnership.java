package com.td.application.duthao;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.duthao.DraftProcessDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 03/10/2026 (cutover xoá vết / xoá tệp ở "Quá trình dự thảo" — {@code Files/duthaofile.jsx}): tải 1
 * dòng {@code draft_process} và kiểm tra người đang đăng nhập là NGƯỜI TẠO dòng đó — cùng điều kiện
 * {@code duthaofile.jsx#isOwnProcessRecord} ở FE: {@code user_creator} = tài khoản; dữ liệu cũ chỉ có
 * {@code creator} = tài khoản (xem {@link DraftProcessActorFields}).
 */
@Component
@RequiredArgsConstructor
public class DraftProcessOwnership {

    static final String NOT_FOUND_MESSAGE = "Không tìm thấy dòng quá trình dự thảo";
    static final String NOT_OWNER_MESSAGE = "Chỉ người tạo dòng quá trình dự thảo mới được xoá";

    private final DraftProcessDocumentRepository draftProcessDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;

    /** Dòng quá trình + field động + tài khoản người thao tác, khi người đó là người tạo dòng. */
    public record OwnedProcess(DraftProcessDocument process, Map<String, Object> fields, String username) {
    }

    public Result<OwnedProcess> loadOwned(UUID processId) {
        var process = processId == null ? null : draftProcessDocumentRepository.findById(processId).orElse(null);
        if (process == null || process.isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        String username = currentUserProvider.currentUsername().orElse(null);
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(process.getDynamicFieldsJson());
        if (!isCreator(fields, username)) {
            return Result.failure(NOT_OWNER_MESSAGE);
        }
        return Result.success(new OwnedProcess(process, fields, username));
    }

    static boolean isCreator(Map<String, Object> fields, String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        String userCreator = asText(fields.get("user_creator"));
        if (!userCreator.isBlank()) {
            return userCreator.equalsIgnoreCase(username.trim());
        }
        return asText(fields.get("creator")).equalsIgnoreCase(username.trim());
    }

    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
