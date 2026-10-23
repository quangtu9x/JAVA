package com.td.infrastructure.fileregistry;

import com.td.application.documents.SharedDocumentFilesReleaser;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

/**
 * Xem javadoc {@link SharedDocumentFilesReleaser}. Xóa dòng {@code file_metadata} dùng chung trong transaction
 * của tenant (cùng lúc với xóa văn bản), nhả tham chiếu ở DB sổ đăng ký SAU KHI commit — 2 database khác nhau,
 * nhả trước commit mà tenant rollback thì object có thể bị dọn trong khi dòng vẫn còn. Nhả lỗi chỉ làm object
 * không được thu hồi (không bao giờ xóa nhầm).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SharedDocumentFilesReleaserImpl implements SharedDocumentFilesReleaser {

    private final FileMetadataJpaRepository fileMetadataRepo;
    private final SharedFileRegistryClient registry;

    @Override
    public void releaseSharedFiles(UUID documentId) {
        List<FileMetadataEntity> shared = fileMetadataRepo.findAllByDocumentId(documentId).stream()
            .filter(f -> Boolean.TRUE.equals(f.getIsShared()))
            .toList();
        if (shared.isEmpty()) {
            return;
        }
        fileMetadataRepo.deleteAll(shared);
        List<UUID> ids = shared.stream().map(FileMetadataEntity::getId).toList();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    release(documentId, ids);
                }
            });
        } else {
            release(documentId, ids);
        }
    }

    private void release(UUID documentId, List<UUID> ids) {
        if (!registry.isEnabled()) {
            return;
        }
        try {
            registry.release(ids);
        } catch (RuntimeException ex) {
            log.error("Khong nha duoc tham chieu tep dung chung {} cua van ban {} (object se khong duoc thu hoi)",
                ids, documentId, ex);
        }
    }
}
