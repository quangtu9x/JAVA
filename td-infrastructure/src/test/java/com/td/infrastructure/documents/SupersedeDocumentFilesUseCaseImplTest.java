package com.td.infrastructure.documents;

import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Yêu cầu người dùng 24/09/2026 — xem javadoc {@link SupersedeDocumentFilesUseCaseImpl}. */
@ExtendWith(MockitoExtension.class)
class SupersedeDocumentFilesUseCaseImplTest {

    @Mock
    private FileMetadataJpaRepository fileMetadataRepo;
    @Mock
    private VbdiFilesJsonbSync vbdiFilesJsonbSync;
    @Mock
    private VbdenFilesJsonbSync vbdenFilesJsonbSync;

    @InjectMocks
    private SupersedeDocumentFilesUseCaseImpl useCase;

    @Test
    void execute_shouldMarkOnlyGivenFilesOfDocument() {
        UUID documentId = UUID.randomUUID();
        UUID historyId = UUID.randomUUID();
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(fileMetadataRepo.markSuperseded(documentId, ids, historyId)).thenReturn(2);

        assertThat(useCase.execute(documentId, ids, historyId)).isEqualTo(2);
        verify(fileMetadataRepo).markSuperseded(documentId, ids, historyId);
        // Bugfix 28/09/2026: tep da thay the phai roi khoi dynamic_fields.files (danh sach doc truc tiep).
        verify(vbdiFilesJsonbSync).syncIfVbdiDocument(documentId);
        verify(vbdenFilesJsonbSync).syncIfVbdenDocument(documentId);
    }

    @Test
    void execute_emptyIds_shouldDoNothing() {
        assertThat(useCase.execute(UUID.randomUUID(), List.of(), null)).isZero();
        assertThat(useCase.execute(null, List.of(UUID.randomUUID()), null)).isZero();
        verifyNoInteractions(fileMetadataRepo, vbdiFilesJsonbSync, vbdenFilesJsonbSync);
    }
}
