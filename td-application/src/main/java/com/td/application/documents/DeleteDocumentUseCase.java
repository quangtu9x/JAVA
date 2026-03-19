package com.td.application.documents;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DeleteDocumentUseCase {

    private final DocumentRepository documentRepository;
    private final DocumentCacheService documentCacheService;
    private final DocumentSearchService documentSearchService;

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

            // TODO: Replace random UUID with authenticated user ID when user context is available.
            document.markAsDeleted(UUID.randomUUID());
            var saved = documentRepository.save(document);
            documentSearchService.delete(saved.getId());
            documentCacheService.evict(saved.getId());
            documentCacheService.evictAllListCaches();

            return Result.success(saved.getId());
        } catch (Exception ex) {
            return Result.failure("Xóa tài liệu thất bại: " + ex.getMessage());
        }
    }
}
