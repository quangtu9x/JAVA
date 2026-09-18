package com.td.infrastructure.documents;

import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.UploadFileRequest;
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

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;

/**
 * Bugfix 27/08/2026 (phản hồi người dùng: nhiều module dùng CHUNG 1 bucket MinIO — tách đường dẫn
 * theo tenant/module để tránh lẫn/nhầm lẫn giữa các module) + DT-007 (tệp đính kèm mang theo
 * {@code process_id} tuỳ chọn — xem javadoc {@link FileMetadataEntity#getProcessId()}).
 */
@ExtendWith(MockitoExtension.class)
class UploadFileUseCaseImplTest {

    @Mock
    private MinioService minioService;
    @Mock
    private FileMetadataJpaRepository fileMetadataRepo;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private VbdiFilesJsonbSync vbdiFilesJsonbSync;
    @Mock
    private VbdenFilesJsonbSync vbdenFilesJsonbSync;
    @Mock
    private VbdiEditHistorySync vbdiEditHistorySync;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private UploadFileUseCaseImpl newUseCase() {
        return new UploadFileUseCaseImpl(
            minioService, fileMetadataRepo, documentCacheService, vbdiFilesJsonbSync, vbdenFilesJsonbSync,
            vbdiEditHistorySync);
    }

    @Test
    void execute_withTenantAndModule_shouldBuildObjectPathWithBothSegments() {
        TenantContext.setCurrentTenant("H05");
        UUID documentId = UUID.randomUUID();
        var request = UploadFileRequest.builder()
            .documentId(documentId)
            .fileContent(new ByteArrayInputStream("noi dung".getBytes()))
            .fileName("bao_cao.docx")
            .module("duthao")
            .build();

        newUseCase().execute(request);

        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(minioService).uploadObject(pathCaptor.capture(), any(), anyLong(), any());
        assertThat(pathCaptor.getValue()).startsWith("H05/duthao/" + documentId + "/");

        ArgumentCaptor<FileMetadataEntity> entityCaptor = ArgumentCaptor.forClass(FileMetadataEntity.class);
        verify(fileMetadataRepo).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getFilePath()).isEqualTo(pathCaptor.getValue());
    }

    // Khong truyen module/tenant -> fallback "documents"/"default", KHONG doi hanh vi cua cac call
    // site chua cap nhat (vd cac module khac ngoai duthao).
    @Test
    void execute_withoutModuleOrTenant_shouldFallBackToDefaultSegments() {
        UUID documentId = UUID.randomUUID();
        var request = UploadFileRequest.builder()
            .documentId(documentId)
            .fileContent(new ByteArrayInputStream("noi dung".getBytes()))
            .fileName("bao_cao.docx")
            .build();

        newUseCase().execute(request);

        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(minioService).uploadObject(pathCaptor.capture(), any(), anyLong(), any());
        assertThat(pathCaptor.getValue()).startsWith("default/documents/" + documentId + "/");
    }

    // DT-007: processId tuy chon duoc luu nguyen ven tren FileMetadataEntity khi co.
    @Test
    void execute_withProcessId_shouldPersistItOnEntity() {
        UUID documentId = UUID.randomUUID();
        UUID processId = UUID.randomUUID();
        var request = UploadFileRequest.builder()
            .documentId(documentId)
            .fileContent(new ByteArrayInputStream("noi dung".getBytes()))
            .fileName("y_kien.pdf")
            .processId(processId)
            .build();

        newUseCase().execute(request);

        ArgumentCaptor<FileMetadataEntity> entityCaptor = ArgumentCaptor.forClass(FileMetadataEntity.class);
        verify(fileMetadataRepo).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getProcessId()).isEqualTo(processId);
        assertThat(entityCaptor.getValue().getDocumentId()).isEqualTo(documentId);
    }

    // Bugfix 09/09/2026 (báo lỗi người dùng: upload đính kèm vbdi 500 "null value in column
    // is_shared violates not-null constraint" — migration V1.0.50 thêm cột NOT NULL DEFAULT FALSE
    // nhưng JPA luôn liệt kê đủ cột trong INSERT nên DEFAULT của DB không áp dụng khi field Java là
    // null): mọi tệp upload MỚI phải có isShared=false tường minh (không phải null), xem javadoc
    // FileMetadataEntity#isShared.
    @Test
    void execute_shouldPersistIsSharedFalse_notNull() {
        UUID documentId = UUID.randomUUID();
        var request = UploadFileRequest.builder()
            .documentId(documentId)
            .fileContent(new ByteArrayInputStream("noi dung".getBytes()))
            .fileName("dinh_kem.docx")
            .build();

        newUseCase().execute(request);

        ArgumentCaptor<FileMetadataEntity> entityCaptor = ArgumentCaptor.forClass(FileMetadataEntity.class);
        verify(fileMetadataRepo).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getIsShared()).isFalse();
    }
}
