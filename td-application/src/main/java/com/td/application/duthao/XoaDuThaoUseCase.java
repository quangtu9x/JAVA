package com.td.application.duthao;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.duthao.DuThaoDocument;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 22/10/2026 (cutover thùng rác dự thảo — route duthao/.../thungrac, view {@code vwUser_Draft_Deleted}):
 * thay Domino {@code qlvb/delete?databasetype=duthao} ({@code deleteoption=trash}, đổi Form sang
 * {@code draft_document_Deleted}) và nút "Khôi phục" ({@code qlvb/add} Form=draft_document). Theo LUẬT
 * "xoá luôn là xoá mềm": {@link #execute} đặt {@code deleted_on}/{@code deleted_by}, {@link #khoiPhuc} xoá 2
 * cột đó. Chỉ người tạo dự thảo ({@code user_creator}) được xoá/khôi phục — khớp kiểm tra FE
 * (toanbo/ItemsList "Bạn không có quyền xoá dự thảo do người khác tạo") và khoá thùng rác (user_creator).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class XoaDuThaoUseCase {

    static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản dự thảo";
    static final String NOT_OWNER_MESSAGE = "Chỉ người tạo dự thảo mới được xoá hoặc khôi phục";

    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    /** Chuyển dự thảo vào thùng rác. Đã xoá trước đó → thành công (idempotent). */
    public Result<UUID> execute(UUID draftId) {
        var owned = loadOwned(draftId);
        if (!owned.isSuccess()) {
            return Result.failure(owned.getError());
        }
        DuThaoDocument document = owned.getData();
        if (document.isDeleted()) {
            return Result.success(document.getId());
        }
        Map<String, Object> before = buildSnapshot(document);
        document.markAsDeleted(resolveCurrentUserId());
        DuThaoDocument saved = duThaoDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logDelete(saved.getId(), before);
        log.info("Xoa du thao {} vao thung rac", saved.getId());
        return Result.success(saved.getId());
    }

    /** Khôi phục dự thảo từ thùng rác. Chưa bị xoá → thành công (idempotent). */
    public Result<UUID> khoiPhuc(UUID draftId) {
        var owned = loadOwned(draftId);
        if (!owned.isSuccess()) {
            return Result.failure(owned.getError());
        }
        DuThaoDocument document = owned.getData();
        if (!document.isDeleted()) {
            return Result.success(document.getId());
        }
        Map<String, Object> before = buildSnapshot(document);
        document.restore();
        DuThaoDocument saved = duThaoDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
        log.info("Khoi phuc du thao {} tu thung rac", saved.getId());
        return Result.success(saved.getId());
    }

    private Result<DuThaoDocument> loadOwned(UUID draftId) {
        DuThaoDocument document = draftId == null ? null : duThaoDocumentRepository.findById(draftId).orElse(null);
        if (document == null || !GetDuThaoDetailUseCase.DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        String username = currentUserProvider.currentUsername().orElse(null);
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        if (!isCreator(fields, username)) {
            return Result.failure(NOT_OWNER_MESSAGE);
        }
        return Result.success(document);
    }

    static boolean isCreator(Map<String, Object> fields, String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        Object userCreator = fields.get("user_creator");
        return userCreator != null && String.valueOf(userCreator).trim().equalsIgnoreCase(username.trim());
    }

    // Ghi đúng người xóa thật (BL-103 audit) — null khi không xác định được user.
    private UUID resolveCurrentUserId() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(AppUser::getId)
            .orElse(null);
    }

    private Map<String, Object> buildSnapshot(DuThaoDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }
}
