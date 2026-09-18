package com.td.infrastructure.documents;

import com.td.application.documents.ListFilesByProcessRequest;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * DT-007 (27/08/2026) — xem javadoc {@link com.td.application.documents.ListDocumentFilesByProcessUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class ListDocumentFilesByProcessUseCaseImplTest {

    @Mock
    private FileMetadataJpaRepository fileMetadataRepo;

    @Test
    void execute_shouldMapEntitiesFilteredByDocumentAndProcessId() {
        ListDocumentFilesByProcessUseCaseImpl useCase = new ListDocumentFilesByProcessUseCaseImpl(fileMetadataRepo);

        UUID documentId = UUID.randomUUID();
        UUID processId = UUID.randomUUID();
        FileMetadataEntity entity = FileMetadataEntity.builder()
            .id(UUID.randomUUID())
            .documentId(documentId)
            .processId(processId)
            .originalFilename("bao_cao.docx")
            .storedFilename(UUID.randomUUID() + ".docx")
            .filePath("H05/duthao/" + documentId + "/x.docx")
            .fileSize(1024L)
            .contentType("application/msword")
            .uploadedAt(LocalDateTime.now())
            .build();
        when(fileMetadataRepo.findAllByDocumentIdAndProcessId(documentId, processId)).thenReturn(List.of(entity));

        var request = ListFilesByProcessRequest.builder().documentId(documentId).processId(processId).build();
        var result = useCase.execute(request);

        assertThat(result.getTotalItems()).isEqualTo(1);
        assertThat(result.getItems().get(0).getOriginalFileName()).isEqualTo("bao_cao.docx");
        assertThat(result.getItems().get(0).getFileSize()).isEqualTo(1024L);
        assertThat(result.getItems().get(0).getDocumentId()).isEqualTo(documentId);
    }

    // Vet du lieu cu khong co process_id (tep gan truoc DT-007) - khong khop truy van theo cap
    // (documentId, processId) cu the, tra rong thay vi loi.
    @Test
    void execute_withNoMatchingFiles_shouldReturnEmptyPage() {
        ListDocumentFilesByProcessUseCaseImpl useCase = new ListDocumentFilesByProcessUseCaseImpl(fileMetadataRepo);
        UUID documentId = UUID.randomUUID();
        UUID processId = UUID.randomUUID();
        when(fileMetadataRepo.findAllByDocumentIdAndProcessId(documentId, processId)).thenReturn(List.of());

        var request = ListFilesByProcessRequest.builder().documentId(documentId).processId(processId).build();
        var result = useCase.execute(request);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotalItems()).isZero();
    }
}
