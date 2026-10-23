package com.td.application.documents;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class HardDeleteDocumentUseCase {

    private final DocumentRepository documentRepository;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final SharedDocumentFilesReleaser sharedDocumentFilesReleaser;

    public Result<UUID> execute(UUID documentId) {
        try {
            var documentOptional = documentRepository.findById(documentId);
            if (documentOptional.isEmpty()) {
                return Result.failure("Không tìm thấy tài liệu với ID: " + documentId);
            }

            var before = DocumentDtoMapper.map(documentOptional.get());

            // Truoc hardDelete, CUNG transaction: dong file_metadata dung chung (van ban den / chuyen xu ly)
            // khong con van ban nao chua -> go va nha tham chieu (xem javadoc SharedDocumentFilesReleaser).
            sharedDocumentFilesReleaser.releaseSharedFiles(documentId);
            documentRepository.hardDelete(documentOptional.get());
            documentCacheService.evict(documentId);
            documentCacheService.evictAllListCaches();
            // Ghi log SAU KHI hard-delete: document_logs không có FK tới documents(id) (xem V1.0.13),
            // nên log vẫn tồn tại độc lập dù document đã bị xoá vĩnh viễn.
            documentLogService.logHardDelete(documentId, before);
            return Result.success(documentId);
        } catch (Exception ex) {
            log.error("Xóa vĩnh viễn tài liệu thất bại", ex);
            return Result.failure("Xóa vĩnh viễn tài liệu thất bại: " + ex.getMessage());
        }
    }
}
