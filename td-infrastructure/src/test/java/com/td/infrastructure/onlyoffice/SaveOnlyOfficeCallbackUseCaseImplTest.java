package com.td.infrastructure.onlyoffice;

import com.td.application.onlyoffice.SaveOnlyOfficeCallbackRequest;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Yêu cầu người dùng 25/09/2026: OnlyOffice lưu bản sửa thành TỆP MỚI, không ghi đè tệp gốc — xem javadoc
 * {@link SaveOnlyOfficeCallbackUseCaseImpl}.
 */
@ExtendWith(MockitoExtension.class)
class SaveOnlyOfficeCallbackUseCaseImplTest {

    @Mock
    private FileMetadataJpaRepository fileMetadataRepository;
    @Mock
    private MinioService minioService;

    @InjectMocks
    private SaveOnlyOfficeCallbackUseCaseImpl useCase;

    private final UUID documentId = UUID.randomUUID();

    private FileMetadataEntity sourceFile() {
        return FileMetadataEntity.builder()
            .id(UUID.randomUUID())
            .documentId(documentId)
            .originalFilename("QuyetDinh_v1.docx")
            .storedFilename("abc.docx")
            .filePath("h05_01/duthao/" + documentId + "/abc.docx")
            .fileExtension("docx")
            .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            .fileSize(100L)
            .build();
    }

    private SaveOnlyOfficeCallbackRequest request(FileMetadataEntity source, String key) {
        return SaveOnlyOfficeCallbackRequest.builder()
            .documentId(documentId)
            .fileId(source.getId())
            .content(new ByteArrayInputStream(new byte[] {1, 2, 3}))
            .contentLength(3)
            .editSessionKey(key)
            .username("vanthu01")
            .build();
    }

    @Test
    void firstSaveOfSession_createsNewFile_andNeverWritesSourceObject() {
        FileMetadataEntity source = sourceFile();
        when(fileMetadataRepository.findByIdAndDocumentId(source.getId(), documentId)).thenReturn(Optional.of(source));
        when(fileMetadataRepository.findFirstByDocumentIdAndSourceFileIdAndEditSessionKey(documentId, source.getId(), "k1"))
            .thenReturn(Optional.empty());
        when(minioService.getBucketName()).thenReturn("td-bucket");

        assertThat(useCase.execute(request(source, "k1")).isSuccess()).isTrue();

        ArgumentCaptor<FileMetadataEntity> saved = ArgumentCaptor.forClass(FileMetadataEntity.class);
        verify(fileMetadataRepository).save(saved.capture());
        FileMetadataEntity created = saved.getValue();
        assertThat(created.getId()).isNotEqualTo(source.getId());
        assertThat(created.getSourceFileId()).isEqualTo(source.getId());
        assertThat(created.getEditSessionKey()).isEqualTo("k1");
        assertThat(created.getDocumentId()).isEqualTo(documentId);
        assertThat(created.getFileSize()).isEqualTo(3L);
        assertThat(created.getFilePath()).startsWith("h05_01/duthao/" + documentId + "/").isNotEqualTo(source.getFilePath());
        // Tên: <tên gốc ĐẦY ĐỦ>_<username>_<ddMMyyyyHHmmss>.<đuôi> (bugfix 25/09: không cắt tại dấu "_")
        assertThat(created.getOriginalFilename()).matches("QuyetDinh_v1_vanthu01_\\d{14}\\.docx");
        verify(minioService).uploadObject(eq(created.getFilePath()), any(), eq(3L), anyString());
        verify(minioService, never()).uploadObject(eq(source.getFilePath()), any(), anyLong(), any());
        assertThat(source.getFileSize()).isEqualTo(100L);
    }

    @Test
    void laterSaveOfSameSession_overwritesThatSessionFile_noDuplicate() {
        FileMetadataEntity source = sourceFile();
        FileMetadataEntity sessionFile = FileMetadataEntity.builder()
            .id(UUID.randomUUID()).documentId(documentId).sourceFileId(source.getId()).editSessionKey("k1")
            .originalFilename("QuyetDinh_vanthu01_25092026101010.docx")
            .filePath("h05_01/duthao/" + documentId + "/new.docx").contentType("x").fileSize(3L).build();
        when(fileMetadataRepository.findByIdAndDocumentId(source.getId(), documentId)).thenReturn(Optional.of(source));
        when(fileMetadataRepository.findFirstByDocumentIdAndSourceFileIdAndEditSessionKey(documentId, source.getId(), "k1"))
            .thenReturn(Optional.of(sessionFile));

        assertThat(useCase.execute(request(source, "k1")).isSuccess()).isTrue();

        verify(minioService).uploadObject(eq(sessionFile.getFilePath()), any(), eq(3L), eq("x"));
        ArgumentCaptor<FileMetadataEntity> saved = ArgumentCaptor.forClass(FileMetadataEntity.class);
        verify(fileMetadataRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(sessionFile.getId());
    }

    @Test
    void unknownSourceFile_fails() {
        FileMetadataEntity source = sourceFile();
        when(fileMetadataRepository.findByIdAndDocumentId(source.getId(), documentId)).thenReturn(Optional.empty());

        assertThat(useCase.execute(request(source, "k1")).isSuccess()).isFalse();
        verify(fileMetadataRepository, never()).save(any());
    }

    @Test
    void editingAnEditedCopy_usesRootOriginalName_noStackedSuffixes() {
        // Bugfix 25/09/2026: sửa tiếp 1 bản ĐÃ sửa -> tên lấy theo tệp GỐC ban đầu (qua source_file_id),
        // không thành "..._u1_<ngày>_u2_<ngày>".
        FileMetadataEntity root = sourceFile();
        root.setOriginalFilename("phan_cong_ct_cac_pct_cuc_cds_2025_v4_1_final.docx");
        FileMetadataEntity editedCopy = FileMetadataEntity.builder()
            .id(UUID.randomUUID()).documentId(documentId).sourceFileId(root.getId())
            .originalFilename("phan_cong_ct_cac_pct_cuc_cds_2025_v4_1_final_skhcn_25092026154136.docx")
            .filePath("h05_01/duthao/" + documentId + "/copy.docx").fileExtension("docx").contentType("x").build();
        when(fileMetadataRepository.findByIdAndDocumentId(editedCopy.getId(), documentId)).thenReturn(Optional.of(editedCopy));
        when(fileMetadataRepository.findFirstByDocumentIdAndSourceFileIdAndEditSessionKey(documentId, editedCopy.getId(), "k2"))
            .thenReturn(Optional.empty());
        when(fileMetadataRepository.findById(root.getId())).thenReturn(Optional.of(root));

        assertThat(useCase.execute(request(editedCopy, "k2")).isSuccess()).isTrue();

        ArgumentCaptor<FileMetadataEntity> saved = ArgumentCaptor.forClass(FileMetadataEntity.class);
        verify(fileMetadataRepository).save(saved.capture());
        assertThat(saved.getValue().getOriginalFilename())
            .matches("phan_cong_ct_cac_pct_cuc_cds_2025_v4_1_final_vanthu01_\\d{14}\\.docx");
        assertThat(saved.getValue().getSourceFileId()).isEqualTo(editedCopy.getId());
    }

    @Test
    void buildEditedFileName_keepsFullOriginalName() {
        assertThat(SaveOnlyOfficeCallbackUseCaseImpl.buildEditedFileName("phan_cong_ct_cac_pct_2025_v4_final.xlsx", "u2"))
            .matches("phan_cong_ct_cac_pct_2025_v4_final_u2_\\d{14}\\.xlsx");
        assertThat(SaveOnlyOfficeCallbackUseCaseImpl.buildEditedFileName("TaiLieu.docx", null))
            .matches("TaiLieu_user_\\d{14}\\.docx");
    }
}
