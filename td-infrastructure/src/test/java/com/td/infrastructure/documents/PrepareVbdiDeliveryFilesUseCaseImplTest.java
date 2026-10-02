package com.td.infrastructure.documents;

import com.td.application.documents.PrepareVbdiDeliveryFilesRequest;
import com.td.application.documents.VbdiDeliveryFileRef;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bổ sung 07/09/2026 (yêu cầu người dùng — xem javadoc {@link PrepareVbdiDeliveryFilesUseCaseImpl}).
 */
@ExtendWith(MockitoExtension.class)
class PrepareVbdiDeliveryFilesUseCaseImplTest {

    @Mock
    private MinioService minioService;
    @Mock
    private FileMetadataJpaRepository fileMetadataRepo;

    private PrepareVbdiDeliveryFilesUseCaseImpl newUseCase() {
        return new PrepareVbdiDeliveryFilesUseCaseImpl(minioService, fileMetadataRepo);
    }

    private FileMetadataEntity sourceFile(UUID documentId, String filename, String ext) {
        return FileMetadataEntity.builder()
            .id(UUID.randomUUID())
            .documentId(documentId)
            .originalFilename(filename)
            .storedFilename(UUID.randomUUID() + "." + ext)
            .filePath("H05/vbdi/" + documentId + "/" + UUID.randomUUID() + "." + ext)
            .fileSize(2048L)
            .contentType("application/pdf")
            .fileExtension(ext)
            .fileCategory("DOCUMENT")
            .bucketName("td-webapi-files")
            .uploadedAt(LocalDateTime.now())
            .isPublic(false)
            .build();
    }

    @Test
    void execute_withFiles_shouldCopyEachToNewNamespaceAndReturnRefs() {
        UUID documentId = UUID.randomUUID();
        when(minioService.getBucketName()).thenReturn("td-webapi-files");
        FileMetadataEntity file1 = sourceFile(documentId, "to_trinh.pdf", "pdf");
        FileMetadataEntity file2 = sourceFile(documentId, "phu_luc.docx", "docx");
        when(fileMetadataRepo.findAllByDocumentId(documentId)).thenReturn(List.of(file1, file2));

        var request = new PrepareVbdiDeliveryFilesRequest();
        request.setDocumentId(documentId);
        request.setSenderIdentifier("H05.01");

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).hasSize(2);

        // Copy server-side qua minioService.copyObject - KHONG tai byte ve.
        ArgumentCaptor<String> sourceCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> destCaptor = ArgumentCaptor.forClass(String.class);
        verify(minioService, times(2)).copyObject(sourceCaptor.capture(), destCaptor.capture());
        assertThat(sourceCaptor.getAllValues()).containsExactly(file1.getFilePath(), file2.getFilePath());
        // Namespace RIENG, tach han khoi duong dan goc - co mang ma don vi gui + ngay + documentId.
        assertThat(destCaptor.getAllValues())
            .allSatisfy(path -> assertThat(path)
                .startsWith("vbdi-delivery/H05.01/")
                .contains("/" + documentId + "/"));

        // Ket qua tra ve dung ten/size/bucket cua tung tep, tro toi vi tri MOI (khong phai filePath goc).
        VbdiDeliveryFileRef ref1 = result.getData().get(0);
        assertThat(ref1.getOriginalFilename()).isEqualTo("to_trinh.pdf");
        assertThat(ref1.getSize()).isEqualTo(2048L);
        assertThat(ref1.getBucketName()).isEqualTo("td-webapi-files");
        assertThat(ref1.getObjectKey()).isNotEqualTo(file1.getFilePath());

        // Ghi kem 1 dong file_metadata "kiem toan" cho MOI tep, khong gan document_id cu the.
        ArgumentCaptor<FileMetadataEntity> auditCaptor = ArgumentCaptor.forClass(FileMetadataEntity.class);
        verify(fileMetadataRepo, times(2)).save(auditCaptor.capture());
        assertThat(auditCaptor.getAllValues())
            .allSatisfy(entity -> {
                assertThat(entity.getDocumentId()).isNull();
                assertThat(entity.getFileCategory()).isEqualTo("VBDI_DELIVERY");
                // Bugfix 09/09/2026 (xem javadoc FileMetadataEntity#isShared): NOT NULL o DB.
                assertThat(entity.getIsShared()).isFalse();
            });
    }

    @Test
    void execute_whenSourceDocumentHasNoFiles_shouldReturnEmptyListNotError() {
        UUID documentId = UUID.randomUUID();
        when(fileMetadataRepo.findAllByDocumentId(documentId)).thenReturn(List.of());

        var request = new PrepareVbdiDeliveryFilesRequest();
        request.setDocumentId(documentId);

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEmpty();
    }

    @Test
    void execute_whenDocumentIdMissing_shouldReturnEmptyListWithoutQuerying() {
        var result = newUseCase().execute(new PrepareVbdiDeliveryFilesRequest());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEmpty();
        verify(fileMetadataRepo, never()).findAllByDocumentId(any());
    }

    /**
     * Bổ sung 07/09/2026 (yêu cầu người dùng "tránh mọi trường hợp có khả năng xảy ra lỗi đính
     * kèm"): 1 tệp lỗi khi copy KHÔNG được làm hỏng các tệp khác trong CÙNG văn bản đi.
     */
    @Test
    void execute_whenOneFileCopyFails_shouldSkipItButStillReturnTheOthers() {
        UUID documentId = UUID.randomUUID();
        when(minioService.getBucketName()).thenReturn("td-webapi-files");
        FileMetadataEntity ok = sourceFile(documentId, "ok.pdf", "pdf");
        FileMetadataEntity broken = sourceFile(documentId, "broken.pdf", "pdf");
        when(fileMetadataRepo.findAllByDocumentId(documentId)).thenReturn(List.of(broken, ok));
        // Stub THEO THU TU goi (broken truoc, ok sau): lan 1 nem loi, lan 2 tro di chay binh thuong.
        doThrow(new RuntimeException("MinIO tam thoi khong kha dung"))
            .doNothing()
            .when(minioService).copyObject(anyString(), anyString());

        var request = new PrepareVbdiDeliveryFilesRequest();
        request.setDocumentId(documentId);

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getOriginalFilename()).isEqualTo("ok.pdf");
    }

    /**
     * Bổ sung 21/09/2026 (yêu cầu người dùng: "nếu check vào phát hành kèm văn bản đến thì khi gửi
     * phải gửi tất cả những file đính kèm của văn bản đến theo" — xem javadoc
     * {@code PrepareVbdiDeliveryFilesRequest#getAdditionalSourceDocumentIds}): tệp của CÁC văn bản
     * đến tham chiếu CŨNG phải được copy, CÙNG namespace với tệp của chính văn bản đi.
     */
    @Test
    void execute_withAdditionalSourceDocumentIds_shouldAlsoCopyTheirFiles() {
        UUID documentId = UUID.randomUUID();
        UUID vbdenId = UUID.randomUUID();
        when(minioService.getBucketName()).thenReturn("td-webapi-files");
        FileMetadataEntity vbdiFile = sourceFile(documentId, "to_trinh.pdf", "pdf");
        FileMetadataEntity vbdenFile = sourceFile(vbdenId, "cong_van_den.pdf", "pdf");
        when(fileMetadataRepo.findAllByDocumentId(documentId)).thenReturn(List.of(vbdiFile));
        when(fileMetadataRepo.findAllByDocumentId(vbdenId)).thenReturn(List.of(vbdenFile));

        var request = new PrepareVbdiDeliveryFilesRequest();
        request.setDocumentId(documentId);
        request.setSenderIdentifier("H05.01");
        request.setAdditionalSourceDocumentIds(List.of(vbdenId));

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).extracting(VbdiDeliveryFileRef::getOriginalFilename)
            .containsExactly("to_trinh.pdf", "cong_van_den.pdf");
        verify(minioService).copyObject(org.mockito.ArgumentMatchers.eq(vbdiFile.getFilePath()), any());
        verify(minioService).copyObject(org.mockito.ArgumentMatchers.eq(vbdenFile.getFilePath()), any());
    }

    @Test
    void execute_withNullAdditionalSourceDocumentIds_shouldOnlyCopyOwnFiles() {
        UUID documentId = UUID.randomUUID();
        when(minioService.getBucketName()).thenReturn("td-webapi-files");
        FileMetadataEntity vbdiFile = sourceFile(documentId, "to_trinh.pdf", "pdf");
        when(fileMetadataRepo.findAllByDocumentId(documentId)).thenReturn(List.of(vbdiFile));

        var request = new PrepareVbdiDeliveryFilesRequest();
        request.setDocumentId(documentId);
        request.setSenderIdentifier("H05.01");

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).hasSize(1);
    }

    /**
     * Bổ sung 30/09/2026: nhóm hiển thị phía nơi nhận — tệp văn bản đi theo {@code tepkemtheo}, tệp
     * "Phát hành kèm văn bản đến" luôn là "Tệp kèm theo".
     */
    @Test
    void execute_shouldAssignAttachmentGroupPerFile() {
        UUID documentId = UUID.randomUUID();
        UUID vbdenId = UUID.randomUUID();
        when(minioService.getBucketName()).thenReturn("td-webapi-files");
        FileMetadataEntity banHanh = sourceFile(documentId, "quyet_dinh.pdf", "pdf");
        FileMetadataEntity kemTheo = sourceFile(documentId, "Phu_Luc.docx", "docx");
        FileMetadataEntity vbdenFile = sourceFile(vbdenId, "cong_van_den.pdf", "pdf");
        when(fileMetadataRepo.findAllByDocumentId(documentId)).thenReturn(List.of(banHanh, kemTheo));
        when(fileMetadataRepo.findAllByDocumentId(vbdenId)).thenReturn(List.of(vbdenFile));

        var request = new PrepareVbdiDeliveryFilesRequest();
        request.setDocumentId(documentId);
        request.setAdditionalSourceDocumentIds(List.of(vbdenId));
        request.setAttachedFilenames(List.of(" phu_luc.docx "));

        var result = newUseCase().execute(request);

        assertThat(result.getData()).extracting(VbdiDeliveryFileRef::getAttachmentGroup)
            .containsExactly(VbdiDeliveryFileRef.GROUP_TEP_BAN_HANH, VbdiDeliveryFileRef.GROUP_TEP_KEM_THEO,
                VbdiDeliveryFileRef.GROUP_TEP_KEM_THEO);
    }
}
