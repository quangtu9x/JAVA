package com.td.infrastructure.documents;

import com.td.application.documents.FileDto;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bugfix 31/08/2026 ("ở danh sách chưa thấy hiển thị tệp đính kèm") — xem javadoc
 * {@link com.td.application.documents.ListFilesForDocumentsUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class ListFilesForDocumentsUseCaseImplTest {

    @Mock
    private FileMetadataJpaRepository fileMetadataRepo;

    @Test
    void execute_shouldGroupFilesByDocumentId() {
        ListFilesForDocumentsUseCaseImpl useCase = new ListFilesForDocumentsUseCaseImpl(fileMetadataRepo);

        UUID documentId1 = UUID.randomUUID();
        UUID documentId2 = UUID.randomUUID();
        FileMetadataEntity entity1 = FileMetadataEntity.builder()
            .id(UUID.randomUUID())
            .documentId(documentId1)
            .originalFilename("bao_cao.docx")
            .storedFilename(UUID.randomUUID() + ".docx")
            .filePath("H05/vbden/" + documentId1 + "/x.docx")
            .fileSize(1024L)
            .contentType("application/msword")
            .uploadedAt(LocalDateTime.now())
            .build();
        FileMetadataEntity entity2 = FileMetadataEntity.builder()
            .id(UUID.randomUUID())
            .documentId(documentId2)
            .originalFilename("phu_luc.pdf")
            .storedFilename(UUID.randomUUID() + ".pdf")
            .filePath("H05/vbden/" + documentId2 + "/y.pdf")
            .fileSize(2048L)
            .contentType("application/pdf")
            .uploadedAt(LocalDateTime.now())
            .build();
        when(fileMetadataRepo.findAllByDocumentIdIn(List.of(documentId1, documentId2)))
            .thenReturn(List.of(entity1, entity2));

        Map<UUID, List<FileDto>> result = useCase.execute(List.of(documentId1, documentId2));

        assertThat(result).containsOnlyKeys(documentId1, documentId2);
        assertThat(result.get(documentId1)).hasSize(1);
        assertThat(result.get(documentId1).get(0).getOriginalFileName()).isEqualTo("bao_cao.docx");
        assertThat(result.get(documentId2).get(0).getOriginalFileName()).isEqualTo("phu_luc.pdf");
    }

    @Test
    void execute_withEmptyDocumentIds_shouldReturnEmptyMapWithoutQuerying() {
        ListFilesForDocumentsUseCaseImpl useCase = new ListFilesForDocumentsUseCaseImpl(fileMetadataRepo);

        Map<UUID, List<FileDto>> result = useCase.execute(List.of());

        assertThat(result).isEmpty();
        verify(fileMetadataRepo, org.mockito.Mockito.never()).findAllByDocumentIdIn(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void execute_withNullDocumentIds_shouldReturnEmptyMapWithoutQuerying() {
        ListFilesForDocumentsUseCaseImpl useCase = new ListFilesForDocumentsUseCaseImpl(fileMetadataRepo);

        Map<UUID, List<FileDto>> result = useCase.execute(null);

        assertThat(result).isEmpty();
    }
}
