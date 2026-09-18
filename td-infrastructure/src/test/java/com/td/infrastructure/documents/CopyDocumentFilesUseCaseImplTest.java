package com.td.infrastructure.documents;

import com.td.application.documents.CopyDocumentFilesRequest;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import com.td.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 27/08/2026 (port "Chuyển văn thư" — xem javadoc {@link CopyDocumentFilesUseCaseImpl}).
 */
@ExtendWith(MockitoExtension.class)
class CopyDocumentFilesUseCaseImplTest {

    @Mock
    private MinioService minioService;
    @Mock
    private FileMetadataJpaRepository fileMetadataRepo;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private CopyDocumentFilesUseCaseImpl newUseCase() {
        return new CopyDocumentFilesUseCaseImpl(minioService, fileMetadataRepo);
    }

    @Test
    void execute_withFiles_shouldCopyEachViaMinioAndSaveNewMetadata() {
        UUID sourceDocumentId = UUID.randomUUID();
        UUID targetDocumentId = UUID.randomUUID();
        TenantContext.setCurrentTenant("H05");
        when(minioService.getBucketName()).thenReturn("td-bucket");

        FileMetadataEntity source = FileMetadataEntity.builder()
            .id(UUID.randomUUID())
            .documentId(sourceDocumentId)
            .originalFilename("baocao.docx")
            .storedFilename(UUID.randomUUID() + ".docx")
            .filePath("H05/duthao/" + sourceDocumentId + "/abc.docx")
            .fileSize(1234L)
            .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            .fileExtension("docx")
            .fileCategory("DOCUMENT")
            .bucketName("td-bucket")
            .uploadedAt(LocalDateTime.now())
            .isPublic(false)
            .build();
        when(fileMetadataRepo.findAllByDocumentId(sourceDocumentId)).thenReturn(List.of(source));

        var request = CopyDocumentFilesRequest.builder()
            .sourceDocumentId(sourceDocumentId)
            .targetDocumentId(targetDocumentId)
            .module("vbdi")
            .build();

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(1);

        ArgumentCaptor<String> destCaptor = ArgumentCaptor.forClass(String.class);
        verify(minioService).copyObject(org.mockito.ArgumentMatchers.eq(source.getFilePath()), destCaptor.capture());
        assertThat(destCaptor.getValue()).startsWith("H05/vbdi/" + targetDocumentId + "/");
        assertThat(destCaptor.getValue()).endsWith(".docx");

        ArgumentCaptor<FileMetadataEntity> savedCaptor = ArgumentCaptor.forClass(FileMetadataEntity.class);
        verify(fileMetadataRepo).save(savedCaptor.capture());
        var saved = savedCaptor.getValue();
        assertThat(saved.getId()).isNotEqualTo(source.getId());
        assertThat(saved.getDocumentId()).isEqualTo(targetDocumentId);
        assertThat(saved.getProcessId()).isNull();
        assertThat(saved.getOriginalFilename()).isEqualTo("baocao.docx");
        assertThat(saved.getFilePath()).isEqualTo(destCaptor.getValue());
        assertThat(saved.getFileSize()).isEqualTo(1234L);
        assertThat(saved.getFileExtension()).isEqualTo("docx");
        // Bugfix 09/09/2026 (xem javadoc FileMetadataEntity#isShared): NOT NULL o DB - dong copy
        // moi phai la false tuong minh, khong duoc null.
        assertThat(saved.getIsShared()).isFalse();
    }

    // Bugfix 09/09/2026: du nguon la 1 dong "dung chung" (isShared=true, vd ban forward_unit), ban
    // COPY VAT LY that (minioService.copyObject) van phai tu so huu rieng - KHONG ke thua co nay.
    @Test
    void execute_whenSourceIsShared_shouldNotPropagateIsSharedToCopy() {
        UUID sourceDocumentId = UUID.randomUUID();
        UUID targetDocumentId = UUID.randomUUID();
        when(minioService.getBucketName()).thenReturn("td-bucket");

        FileMetadataEntity source = FileMetadataEntity.builder()
            .id(UUID.randomUUID())
            .documentId(sourceDocumentId)
            .originalFilename("baocao.docx")
            .storedFilename(UUID.randomUUID() + ".docx")
            .filePath("H05/vbden/" + sourceDocumentId + "/abc.docx")
            .fileSize(1234L)
            .fileExtension("docx")
            .fileCategory("DOCUMENT")
            .bucketName("td-bucket")
            .uploadedAt(LocalDateTime.now())
            .isPublic(false)
            .isShared(true)
            .build();
        when(fileMetadataRepo.findAllByDocumentId(sourceDocumentId)).thenReturn(List.of(source));

        var request = CopyDocumentFilesRequest.builder()
            .sourceDocumentId(sourceDocumentId)
            .targetDocumentId(targetDocumentId)
            .module("vbden")
            .build();

        newUseCase().execute(request);

        ArgumentCaptor<FileMetadataEntity> savedCaptor = ArgumentCaptor.forClass(FileMetadataEntity.class);
        verify(fileMetadataRepo).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getIsShared()).isFalse();
    }

    @Test
    void execute_withNoSourceFiles_shouldReturnZeroWithoutTouchingMinio() {
        UUID sourceDocumentId = UUID.randomUUID();
        UUID targetDocumentId = UUID.randomUUID();
        when(fileMetadataRepo.findAllByDocumentId(sourceDocumentId)).thenReturn(List.of());

        var request = CopyDocumentFilesRequest.builder()
            .sourceDocumentId(sourceDocumentId)
            .targetDocumentId(targetDocumentId)
            .build();

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(0);
        verify(minioService, never()).copyObject(anyString(), anyString());
        verify(fileMetadataRepo, never()).save(any());
    }

    @Test
    void execute_withoutModuleOrTenant_shouldFallBackToDefaultSegments() {
        UUID sourceDocumentId = UUID.randomUUID();
        UUID targetDocumentId = UUID.randomUUID();
        when(minioService.getBucketName()).thenReturn("td-bucket");
        FileMetadataEntity source = FileMetadataEntity.builder()
            .id(UUID.randomUUID())
            .documentId(sourceDocumentId)
            .originalFilename("a.pdf")
            .storedFilename(UUID.randomUUID() + ".pdf")
            .filePath("default/duthao/" + sourceDocumentId + "/x.pdf")
            .fileSize(1L)
            .fileExtension("pdf")
            .fileCategory("DOCUMENT")
            .bucketName("td-bucket")
            .uploadedAt(LocalDateTime.now())
            .build();
        when(fileMetadataRepo.findAllByDocumentId(sourceDocumentId)).thenReturn(List.of(source));

        var request = CopyDocumentFilesRequest.builder()
            .sourceDocumentId(sourceDocumentId)
            .targetDocumentId(targetDocumentId)
            .build();

        newUseCase().execute(request);

        ArgumentCaptor<String> destCaptor = ArgumentCaptor.forClass(String.class);
        verify(minioService).copyObject(anyString(), destCaptor.capture());
        assertThat(destCaptor.getValue()).startsWith("default/documents/" + targetDocumentId + "/");
    }

    // Dinh chinh 27/08/2026 (lan 2, nguoi dung xac nhan lai): filter theo TEN tep - xem javadoc
    // CopyDocumentFilesRequest.
    @Test
    void execute_withFilenamesFilter_shouldOnlyCopyMatchingFiles() {
        UUID sourceDocumentId = UUID.randomUUID();
        UUID targetDocumentId = UUID.randomUUID();
        when(minioService.getBucketName()).thenReturn("td-bucket");
        FileMetadataEntity matching = FileMetadataEntity.builder()
            .id(UUID.randomUUID()).documentId(sourceDocumentId).originalFilename("baocao.docx")
            .storedFilename("a.docx").filePath("default/documents/" + sourceDocumentId + "/a.docx")
            .fileSize(1L).fileCategory("DOCUMENT").bucketName("td-bucket").uploadedAt(LocalDateTime.now()).build();
        FileMetadataEntity nonMatching = FileMetadataEntity.builder()
            .id(UUID.randomUUID()).documentId(sourceDocumentId).originalFilename("ykien_rieng.pdf")
            .storedFilename("b.pdf").filePath("default/documents/" + sourceDocumentId + "/b.pdf")
            .fileSize(1L).fileCategory("DOCUMENT").bucketName("td-bucket").uploadedAt(LocalDateTime.now()).build();
        when(fileMetadataRepo.findAllByDocumentId(sourceDocumentId)).thenReturn(List.of(matching, nonMatching));

        var request = CopyDocumentFilesRequest.builder()
            .sourceDocumentId(sourceDocumentId)
            .targetDocumentId(targetDocumentId)
            .filenames(java.util.Set.of("baocao.docx"))
            .build();

        var result = newUseCase().execute(request);

        assertThat(result.getData()).isEqualTo(1);
        verify(minioService, times(1)).copyObject(anyString(), anyString());
        ArgumentCaptor<FileMetadataEntity> savedCaptor = ArgumentCaptor.forClass(FileMetadataEntity.class);
        verify(fileMetadataRepo, times(1)).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getOriginalFilename()).isEqualTo("baocao.docx");
    }

    // filenames null/rong -> copy TOAN BO (hanh vi mac dinh, giu cho caller khac chua can loc).
    @Test
    void execute_withoutFilenamesFilter_shouldCopyAllFiles() {
        UUID sourceDocumentId = UUID.randomUUID();
        UUID targetDocumentId = UUID.randomUUID();
        when(minioService.getBucketName()).thenReturn("td-bucket");
        FileMetadataEntity a = FileMetadataEntity.builder()
            .id(UUID.randomUUID()).documentId(sourceDocumentId).originalFilename("a.pdf")
            .storedFilename("a.pdf").filePath("default/documents/" + sourceDocumentId + "/a.pdf")
            .fileSize(1L).fileCategory("DOCUMENT").bucketName("td-bucket").uploadedAt(LocalDateTime.now()).build();
        when(fileMetadataRepo.findAllByDocumentId(sourceDocumentId)).thenReturn(List.of(a));

        var request = CopyDocumentFilesRequest.builder()
            .sourceDocumentId(sourceDocumentId)
            .targetDocumentId(targetDocumentId)
            .build();

        var result = newUseCase().execute(request);

        assertThat(result.getData()).isEqualTo(1);
        verify(minioService, times(1)).copyObject(anyString(), anyString());
    }

    @Test
    void execute_withMultipleFiles_shouldCopyAll() {
        UUID sourceDocumentId = UUID.randomUUID();
        UUID targetDocumentId = UUID.randomUUID();
        when(minioService.getBucketName()).thenReturn("td-bucket");
        FileMetadataEntity a = FileMetadataEntity.builder()
            .id(UUID.randomUUID()).documentId(sourceDocumentId).originalFilename("a.pdf")
            .storedFilename("a.pdf").filePath("default/documents/" + sourceDocumentId + "/a.pdf")
            .fileSize(1L).fileCategory("DOCUMENT").bucketName("td-bucket").uploadedAt(LocalDateTime.now()).build();
        FileMetadataEntity b = FileMetadataEntity.builder()
            .id(UUID.randomUUID()).documentId(sourceDocumentId).originalFilename("b.pdf")
            .storedFilename("b.pdf").filePath("default/documents/" + sourceDocumentId + "/b.pdf")
            .fileSize(1L).fileCategory("DOCUMENT").bucketName("td-bucket").uploadedAt(LocalDateTime.now()).build();
        when(fileMetadataRepo.findAllByDocumentId(sourceDocumentId)).thenReturn(List.of(a, b));

        var request = CopyDocumentFilesRequest.builder()
            .sourceDocumentId(sourceDocumentId)
            .targetDocumentId(targetDocumentId)
            .build();

        var result = newUseCase().execute(request);

        assertThat(result.getData()).isEqualTo(2);
        verify(minioService, times(2)).copyObject(anyString(), anyString());
        verify(fileMetadataRepo, times(2)).save(any());
    }
}
