package com.td.application.documents;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DeleteDocumentUseCase {

    private final DocumentRepository documentRepository;
    private final DocumentCacheService documentCacheService;
    private final DocumentSearchService documentSearchService;
    private final DocumentLogService documentLogService;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    public Result<UUID> execute(UUID documentId) {
        try {
            var documentOptional = documentRepository.findById(documentId);
            if (documentOptional.isEmpty()) {
                return Result.failure("Không tìm thấy tài liệu với ID: " + documentId);
            }

            var document = documentOptional.get();
            if (document.isDeleted()) {
                // Đã xóa trước đó — trả về thành công (idempotent)
                documentSearchService.delete(documentId);
                documentCacheService.evict(documentId);
                documentCacheService.evictAllListCaches();
                return Result.success(documentId);
            }

            var before = DocumentDtoMapper.map(document);

            document.markAsDeleted(resolveCurrentUserId());
            var saved = documentRepository.save(document);
            documentSearchService.delete(saved.getId());
            documentCacheService.evict(saved.getId());
            documentCacheService.evictAllListCaches();
            documentLogService.logDelete(saved.getId(), before);

            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Xóa tài liệu thất bại", ex);
            return Result.failure("Xóa tài liệu thất bại: " + ex.getMessage());
        }
    }

    // Ghi đúng người xóa thật (BL-103 audit) — null (không phải UUID ngẫu nhiên) khi không xác định được user
    private UUID resolveCurrentUserId() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(AppUser::getId)
            .orElse(null);
    }
}
