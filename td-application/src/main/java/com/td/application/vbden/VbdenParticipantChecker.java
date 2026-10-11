package com.td.application.vbden;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 09/10/2026 (bugfix 403 lần 2 ở {@code POST /api/v1/vbden/ChuyenXuLy} — demo13, sau lần 24/09):
 * Domino {@code ChuyenXuLy.java} KHÔNG chặn theo role ở đầu vào (QPhanCong/QPhanPhoi/QCapNhatVBDen
 * chỉ quyết định hành vi phụ), người ngoài bị chặn nhờ ACL Reader/Author của tài liệu. Chuyên
 * viên/lãnh đạo nhận văn bản (màn "Chờ xử lý") chuyển tiếp/ghi ý kiến qua CHÍNH action này mà không
 * có quyền phân công. Thay ACL Domino: người đăng nhập có tên trong BẤT KỲ field {@code user_*}
 * (field danh sách thường, bỏ qua {@code *_obj}) hoặc {@code author} của văn bản thì được phép.
 * Dùng trong {@code @PreAuthorize} của VbdenController (OR với gate role cũ).
 */
@Service
@RequiredArgsConstructor
public class VbdenParticipantChecker {

    private static final String DOCUMENT_TYPE = "vbden";

    private final DocumentRepository documentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;

    public boolean isCurrentUserParticipant(UUID documentId) {
        if (documentId == null) {
            return false;
        }
        String username = currentUserProvider.currentUsername().orElse(null);
        if (username == null || username.isBlank()) {
            return false;
        }
        return documentRepository.findById(documentId)
            .filter(doc -> !doc.isDeleted() && DOCUMENT_TYPE.equalsIgnoreCase(doc.getDocumentType()))
            .map(doc -> isParticipant(
                dynamicDocumentFieldsService.parseDynamicFields(doc.getDynamicFieldsJson()), username))
            .orElse(false);
    }

    static boolean isParticipant(Map<String, Object> fields, String username) {
        if (fields == null) {
            return false;
        }
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String lower = key.toLowerCase();
            boolean participantField = (lower.startsWith("user_") && !lower.endsWith("_obj")) || lower.equals("author");
            if (participantField && containsUsername(entry.getValue(), username)) {
                return true;
            }
        }
        return false;
    }

    // Dữ liệu 2 dạng: List (đã parse) hoặc chuỗi nối ";"/"," (xem vbden-migration-context).
    private static boolean containsUsername(Object value, String username) {
        if (value instanceof Collection<?> items) {
            return items.stream().anyMatch(item -> containsUsername(item, username));
        }
        if (value instanceof String text) {
            for (String token : text.split("[;,]")) {
                if (token.trim().equalsIgnoreCase(username)) {
                    return true;
                }
            }
        }
        return false;
    }
}
