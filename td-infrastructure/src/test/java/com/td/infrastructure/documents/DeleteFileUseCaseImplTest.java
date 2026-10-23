package com.td.infrastructure.documents;

import com.td.application.documents.DeleteFileRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.fileregistry.SharedFileRegistryClient;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Yêu cầu người dùng 28/08/2026 ("ghi log chỉnh sửa... lưu cả nhật ký thêm, xoá đính kèm"): test
 * {@link DeleteFileUseCaseImpl} — chưa từng có test trước đây, thêm kèm khi wire
 * {@link VbdiEditHistorySync}.
 */
@ExtendWith(MockitoExtension.class)
class DeleteFileUseCaseImplTest {

    @Mock
    private FileMetadataJpaRepository fileMetadataRepo;
    @Mock
    private MinioService minioService;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private VbdiFilesJsonbSync vbdiFilesJsonbSync;
    @Mock
    private VbdenFilesJsonbSync vbdenFilesJsonbSync;
    @Mock
    private VbdiEditHistorySync vbdiEditHistorySync;
    @Mock
    private SharedFileRegistryClient sharedFileRegistry;

    private DeleteFileUseCaseImpl newUseCase() {
        return new DeleteFileUseCaseImpl(
            fileMetadataRepo, minioService, documentCacheService, vbdiFilesJsonbSync, vbdenFilesJsonbSync,
            vbdiEditHistorySync, sharedFileRegistry);
    }

    private FileMetadataEntity newEntity(UUID documentId, UUID fileId) {
        return FileMetadataEntity.builder()
            .id(fileId)
            .documentId(documentId)
            .originalFilename("bao_cao.docx")
            .storedFilename(fileId + ".docx")
            .filePath("default/vbdi/" + documentId + "/" + fileId + ".docx")
            .fileSize(1024L)
            .build();
    }

    @Test
    void execute_withExistingFile_shouldDeleteAndSyncBothHooks() {
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileMetadataEntity entity = newEntity(documentId, fileId);
        when(fileMetadataRepo.findByIdAndDocumentId(fileId, documentId)).thenReturn(Optional.of(entity));

        var request = DeleteFileRequest.builder().documentId(documentId).fileId(fileId).build();
        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(fileId);
        verify(minioService).removeObject(entity.getFilePath());
        verify(fileMetadataRepo).delete(entity);
        verify(vbdiFilesJsonbSync).syncIfVbdiDocument(documentId);
        verify(vbdiEditHistorySync).logFileRemoved(documentId, "bao_cao.docx");
    }

    @Test
    void execute_whenFileNotFound_shouldReturnFailureWithoutSyncing() {
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(fileMetadataRepo.findByIdAndDocumentId(fileId, documentId)).thenReturn(Optional.empty());

        var request = DeleteFileRequest.builder().documentId(documentId).fileId(fileId).build();
        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(vbdiFilesJsonbSync, never()).syncIfVbdiDocument(org.mockito.ArgumentMatchers.any());
        verify(vbdiEditHistorySync, never()).logFileRemoved(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /**
     * Yêu cầu người dùng 08/09/2026 ("văn bản nhận được bằng chức năng 'chuyển xử lý' thì không cho
     * xóa đính kèm gốc"): dòng {@code file_metadata.is_shared = TRUE} (bản sao vbden.forward_unit
     * "đọc chung" tệp của văn bản gốc ở tenant khác, xem migration V1.0.50) PHẢI bị chặn xóa HOÀN
     * TOÀN — không gọi MinIO, không xóa dòng DB — tránh xóa nhầm object vật lý mà văn bản gốc ở
     * tenant khác vẫn đang tham chiếu.
     */
    @Test
    void execute_whenFileIsShared_shouldBlockDeletionEntirely() {
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileMetadataEntity entity = newEntity(documentId, fileId);
        entity.setIsShared(true);
        when(fileMetadataRepo.findByIdAndDocumentId(fileId, documentId)).thenReturn(Optional.of(entity));

        var request = DeleteFileRequest.builder().documentId(documentId).fileId(fileId).build();
        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("dùng chung");
        verify(minioService, never()).removeObject(org.mockito.ArgumentMatchers.any());
        verify(fileMetadataRepo, never()).delete(org.mockito.ArgumentMatchers.any());
        verify(vbdiFilesJsonbSync, never()).syncIfVbdiDocument(org.mockito.ArgumentMatchers.any());
        verify(vbdenFilesJsonbSync, never()).syncIfVbdenDocument(org.mockito.ArgumentMatchers.any());
        verify(vbdiEditHistorySync, never()).logFileRemoved(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void execute_whenFileIsNotShared_shouldDeleteNormally() {
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileMetadataEntity entity = newEntity(documentId, fileId);
        entity.setIsShared(false);
        when(fileMetadataRepo.findByIdAndDocumentId(fileId, documentId)).thenReturn(Optional.of(entity));

        var request = DeleteFileRequest.builder().documentId(documentId).fileId(fileId).build();
        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(minioService).removeObject(entity.getFilePath());
        verify(fileMetadataRepo).delete(entity);
    }

    /**
     * Yêu cầu người dùng 22/10/2026: tệp gốc (is_shared=FALSE) đã được "Chuyển xử lý" sang đơn vị khác —
     * đơn vị gốc xóa thì chỉ xóa dòng của mình, GIỮ object cho đơn vị đang dùng chung.
     */
    @Test
    void execute_whenOwnerFileStillReferencedByOtherUnits_shouldKeepObject() {
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileMetadataEntity entity = newEntity(documentId, fileId);
        entity.setIsShared(false);
        entity.setBucketName("td-webapi-files");
        when(fileMetadataRepo.findByIdAndDocumentId(fileId, documentId)).thenReturn(Optional.of(entity));
        when(sharedFileRegistry.isEnabled()).thenReturn(true);
        when(sharedFileRegistry.hasActiveRefs("td-webapi-files", entity.getFilePath())).thenReturn(true);

        var result = newUseCase().execute(DeleteFileRequest.builder().documentId(documentId).fileId(fileId).build());

        assertThat(result.isSuccess()).isTrue();
        verify(minioService, never()).removeObject(org.mockito.ArgumentMatchers.any());
        verify(fileMetadataRepo).delete(entity);
    }

    @Test
    void execute_whenRegistryUnavailable_shouldKeepObjectButDeleteRow() {
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileMetadataEntity entity = newEntity(documentId, fileId);
        entity.setIsShared(false);
        entity.setBucketName("td-webapi-files");
        when(fileMetadataRepo.findByIdAndDocumentId(fileId, documentId)).thenReturn(Optional.of(entity));
        when(sharedFileRegistry.isEnabled()).thenReturn(true);
        when(sharedFileRegistry.hasActiveRefs("td-webapi-files", entity.getFilePath()))
            .thenThrow(new IllegalStateException("db down"));

        var result = newUseCase().execute(DeleteFileRequest.builder().documentId(documentId).fileId(fileId).build());

        assertThat(result.isSuccess()).isTrue();
        verify(minioService, never()).removeObject(org.mockito.ArgumentMatchers.any());
        verify(fileMetadataRepo).delete(entity);
    }
}
