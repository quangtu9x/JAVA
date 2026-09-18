package com.td.infrastructure.documents;

import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.RenameFileRequest;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gap "đính kèm" 28/08/2026 ("port luôn gap còn lại đi"): test {@link RenameFileUseCaseImpl}.
 */
@ExtendWith(MockitoExtension.class)
class RenameFileUseCaseImplTest {

    @Mock
    private FileMetadataJpaRepository fileMetadataRepo;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private VbdiFilesJsonbSync vbdiFilesJsonbSync;
    @Mock
    private VbdenFilesJsonbSync vbdenFilesJsonbSync;

    private RenameFileUseCaseImpl newUseCase() {
        return new RenameFileUseCaseImpl(
            fileMetadataRepo, documentCacheService, vbdiFilesJsonbSync, vbdenFilesJsonbSync);
    }

    private FileMetadataEntity newEntity(UUID documentId, UUID fileId) {
        return FileMetadataEntity.builder()
                .id(fileId)
                .documentId(documentId)
                .originalFilename("bao_cao.docx")
                .storedFilename(fileId + ".docx")
                .filePath("default/vbdi/" + documentId + "/" + fileId + ".docx")
                .fileSize(1024L)
                .fileExtension("docx")
                .fileCategory("DOCUMENT")
                .bucketName("documents")
                .build();
    }

    @Test
    void execute_withExistingFile_shouldRenameAndSyncJsonbCache() {
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileMetadataEntity entity = newEntity(documentId, fileId);
        when(fileMetadataRepo.findByIdAndDocumentId(fileId, documentId)).thenReturn(Optional.of(entity));

        var request = RenameFileRequest.builder()
                .documentId(documentId)
                .fileId(fileId)
                .newFileName("bao_cao_moi.docx")
                .build();
        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(fileId);

        ArgumentCaptor<FileMetadataEntity> captor = ArgumentCaptor.forClass(FileMetadataEntity.class);
        verify(fileMetadataRepo).save(captor.capture());
        assertThat(captor.getValue().getOriginalFilename()).isEqualTo("bao_cao_moi.docx");
        assertThat(captor.getValue().getFileExtension()).isEqualTo("docx");

        // stored_filename (khoá vật lý MinIO) KHÔNG được đổi - chỉ đổi tên hiển thị.
        assertThat(captor.getValue().getStoredFilename()).isEqualTo(fileId + ".docx");

        verify(vbdiFilesJsonbSync).syncIfVbdiDocument(documentId);
    }

    @Test
    void execute_whenFileNotFound_shouldReturnFailureWithoutSyncing() {
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(fileMetadataRepo.findByIdAndDocumentId(fileId, documentId)).thenReturn(Optional.empty());

        var request = RenameFileRequest.builder()
                .documentId(documentId)
                .fileId(fileId)
                .newFileName("bao_cao_moi.docx")
                .build();
        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(fileMetadataRepo, never()).save(org.mockito.ArgumentMatchers.any());
        verify(vbdiFilesJsonbSync, never()).syncIfVbdiDocument(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void execute_withBlankNewFileName_shouldReturnFailureWithoutSaving() {
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileMetadataEntity entity = newEntity(documentId, fileId);
        when(fileMetadataRepo.findByIdAndDocumentId(fileId, documentId)).thenReturn(Optional.of(entity));

        var request = RenameFileRequest.builder()
                .documentId(documentId)
                .fileId(fileId)
                .newFileName("   ")
                .build();
        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(fileMetadataRepo, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
