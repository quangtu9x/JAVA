package com.td.application.vbdi;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentSearchService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.vbdi.VbdiDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BL-220 (VBDi Core), yêu cầu người dùng (tách văn bản đi khỏi bảng dùng chung): tương đương
 * {@link com.td.application.documents.DeleteDocumentUseCase} (xoá mềm document) nhưng thao tác trên
 * bảng vật lý ĐỘC LẬP {@code vbdi_documents} (xem {@link VbdiDocument}) thay vì {@code documents}
 * dùng chung — {@code DeleteDocumentUseCase} đọc/ghi qua {@code DocumentRepository} sẽ không bao giờ
 * thấy văn bản đi nữa sau khi tách.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DeleteVbdiDocumentUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đi với ID: ";

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentSearchService documentSearchService;
    private final DocumentLogService documentLogService;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    public Result<UUID> execute(UUID documentId) {
        try {
            var documentOptional = vbdiDocumentRepository.findById(documentId);
            if (documentOptional.isEmpty()) {
                return Result.failure(NOT_FOUND_MESSAGE + documentId);
            }

            var document = documentOptional.get();
            if (document.isDeleted()) {
                // Đã xóa trước đó — trả về thành công (idempotent)
                documentSearchService.delete(documentId);
                documentCacheService.evict(documentId);
                documentCacheService.evictAllListCaches();
                return Result.success(documentId);
            }

            var before = buildSnapshot(document);

            document.markAsDeleted(resolveCurrentUserId());
            var saved = vbdiDocumentRepository.save(document);
            documentSearchService.delete(saved.getId());
            documentCacheService.evict(saved.getId());
            documentCacheService.evictAllListCaches();
            documentLogService.logDelete(saved.getId(), before);

            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Xóa văn bản đi thất bại", ex);
            return Result.failure("Xóa văn bản đi thất bại: " + ex.getMessage());
        }
    }

    // Ghi đúng người xóa thật (BL-103 audit) — null (không phải UUID ngẫu nhiên) khi không xác định được user
    private UUID resolveCurrentUserId() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(AppUser::getId)
            .orElse(null);
    }

    private Map<String, Object> buildSnapshot(VbdiDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }
}