package com.td.infrastructure.fileregistry;

import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Yêu cầu người dùng 22/10/2026: xóa vĩnh viễn văn bản đến → gỡ dòng dùng chung, nhả tham chiếu SAU commit. */
@ExtendWith(MockitoExtension.class)
class SharedDocumentFilesReleaserImplTest {

    @Mock
    private FileMetadataJpaRepository fileMetadataRepo;
    @Mock
    private SharedFileRegistryClient registry;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deletesOnlySharedRowsAndReleasesAfterCommit() {
        UUID documentId = UUID.randomUUID();
        FileMetadataEntity shared = row(true);
        FileMetadataEntity own = row(false);
        when(fileMetadataRepo.findAllByDocumentId(documentId)).thenReturn(List.of(shared, own));
        when(registry.isEnabled()).thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();

        new SharedDocumentFilesReleaserImpl(fileMetadataRepo, registry).releaseSharedFiles(documentId);

        verify(fileMetadataRepo).deleteAll(List.of(shared));
        verify(registry, never()).release(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(registry).release(List.of(shared.getId()));
    }

    @Test
    void noSharedRowsDoesNothing() {
        UUID documentId = UUID.randomUUID();
        when(fileMetadataRepo.findAllByDocumentId(documentId)).thenReturn(List.of(row(false)));

        new SharedDocumentFilesReleaserImpl(fileMetadataRepo, registry).releaseSharedFiles(documentId);

        verify(fileMetadataRepo, never()).deleteAll(any());
        verify(registry, never()).release(any());
    }

    private FileMetadataEntity row(boolean shared) {
        FileMetadataEntity e = FileMetadataEntity.builder().id(UUID.randomUUID()).filePath("p/" + UUID.randomUUID()).build();
        e.setIsShared(shared);
        return e;
    }
}
