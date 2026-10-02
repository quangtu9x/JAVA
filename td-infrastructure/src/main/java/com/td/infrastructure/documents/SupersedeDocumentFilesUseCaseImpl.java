package com.td.infrastructure.documents;

import com.td.application.documents.SupersedeDocumentFilesUseCase;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

/**
 * Yêu cầu người dùng 24/09/2026 — xem javadoc {@link SupersedeDocumentFilesUseCase}: chỉ ghi
 * {@code superseded_at}/{@code superseded_by} (native UPDATE, giới hạn đúng {@code document_id}), KHÔNG
 * xoá dòng, KHÔNG động tới object MinIO — tệp cũ vẫn còn nguyên để khôi phục nếu cần, chỉ không còn
 * đường nào qua ứng dụng để liệt kê/tải (xem {@code @SQLRestriction} trên {@code FileMetadataEntity}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupersedeDocumentFilesUseCaseImpl implements SupersedeDocumentFilesUseCase {

    private final FileMetadataJpaRepository fileMetadataRepo;
    // Bugfix 28/09/2026: tep da thay the bi an khoi MOI truy van JPA (@SQLRestriction) nhung ban sao
    // dynamic_fields.files (danh sach vbdi/vbden doc truc tiep) van giu tep cu - dong bo lai sau khi danh dau.
    private final VbdiFilesJsonbSync vbdiFilesJsonbSync;
    private final VbdenFilesJsonbSync vbdenFilesJsonbSync;

    @Override
    @Transactional
    public int execute(UUID documentId, Collection<UUID> fileIds, UUID supersededBy) {
        if (documentId == null || fileIds == null || fileIds.isEmpty()) {
            return 0;
        }
        int updated = fileMetadataRepo.markSuperseded(documentId, fileIds, supersededBy);
        log.info("Danh dau {} tep da duoc thay the cua document {} (superseded_by={})",
            updated, documentId, supersededBy);
        if (updated > 0) {
            vbdiFilesJsonbSync.syncIfVbdiDocument(documentId);
            vbdenFilesJsonbSync.syncIfVbdenDocument(documentId);
        }
        return updated;
    }
}
