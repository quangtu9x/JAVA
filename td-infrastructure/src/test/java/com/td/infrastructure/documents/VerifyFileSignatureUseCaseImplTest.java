package com.td.infrastructure.documents;

import com.td.application.documents.VerifyFileSignatureRequest;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * BL-221 (VBDi — Ký số & Gửi đi) — DoD (2): {@link VerifyFileSignatureUseCaseImpl} — phần tải file
 * từ MinIO + dispatch sang {@code PdfSignatureVerifier} (đã có test riêng với chữ ký thật ở
 * {@code PdfSignatureVerifierTest}, ở đây chỉ test phần "ống dẫn": file không tồn tại, PDF không
 * có chữ ký, PDF không đọc được).
 */
@ExtendWith(MockitoExtension.class)
class VerifyFileSignatureUseCaseImplTest {

    @Mock
    private FileMetadataJpaRepository fileMetadataRepo;
    @Mock
    private MinioService minioService;

    private VerifyFileSignatureUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new VerifyFileSignatureUseCaseImpl(fileMetadataRepo, minioService);
    }

    @Test
    void execute_withUnknownFile_shouldReturnFailure() {
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(fileMetadataRepo.findByIdAndDocumentId(fileId, documentId)).thenReturn(Optional.empty());

        var request = new VerifyFileSignatureRequest();
        request.setDocumentId(documentId);
        request.setFileId(fileId);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withUnsignedPdf_shouldReturnFailure() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileMetadataEntity file = FileMetadataEntity.builder()
            .id(fileId).documentId(documentId).filePath("documents/" + documentId + "/" + fileId + ".pdf")
            .originalFilename("vbdi.pdf").build();
        when(fileMetadataRepo.findByIdAndDocumentId(fileId, documentId)).thenReturn(Optional.of(file));
        when(minioService.getObject(file.getFilePath())).thenReturn(new ByteArrayInputStream(buildUnsignedPdf()));

        var request = new VerifyFileSignatureRequest();
        request.setDocumentId(documentId);
        request.setFileId(fileId);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("chữ ký số");
    }

    @Test
    void execute_whenMinioThrows_shouldReturnFailureWithoutPropagatingException() {
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        FileMetadataEntity file = FileMetadataEntity.builder()
            .id(fileId).documentId(documentId).filePath("bad/path.pdf").originalFilename("x.pdf").build();
        when(fileMetadataRepo.findByIdAndDocumentId(fileId, documentId)).thenReturn(Optional.of(file));
        when(minioService.getObject(file.getFilePath())).thenThrow(new RuntimeException("MinIO down"));

        var request = new VerifyFileSignatureRequest();
        request.setDocumentId(documentId);
        request.setFileId(fileId);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    private byte[] buildUnsignedPdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            return out.toByteArray();
        }
    }
}
