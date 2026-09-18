package com.td.infrastructure.documents;

import com.td.application.common.models.Result;
import com.td.application.documents.ChuKySoDto;
import com.td.application.documents.VerifyFileSignatureRequest;
import com.td.application.documents.VerifyFileSignatureUseCase;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import com.td.infrastructure.signature.PdfSignatureVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

/**
 * BL-221 (VBDi — Ký số & Gửi đi) — DoD (2): impl thật của {@link VerifyFileSignatureUseCase} — tải
 * byte file từ MinIO rồi xác thực chữ ký số bằng {@link PdfSignatureVerifier} (PDFBox +
 * BouncyCastle). Cùng cách truy cập file với {@code GetFileUseCaseImpl}/{@code UploadFileUseCaseImpl}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyFileSignatureUseCaseImpl implements VerifyFileSignatureUseCase {

    private final FileMetadataJpaRepository fileMetadataRepo;
    private final MinioService minioService;

    @Override
    public Result<List<ChuKySoDto>> execute(VerifyFileSignatureRequest request) {
        var fileOptional = fileMetadataRepo.findByIdAndDocumentId(request.getFileId(), request.getDocumentId());
        if (fileOptional.isEmpty()) {
            return Result.failure("Không tìm thấy file");
        }
        FileMetadataEntity file = fileOptional.get();

        byte[] pdfBytes;
        try (InputStream inputStream = minioService.getObject(file.getFilePath())) {
            pdfBytes = inputStream.readAllBytes();
        } catch (Exception ex) {
            log.warn("Khong doc duoc file {} de xac thuc chu ky: {}", file.getId(), ex.getMessage());
            return Result.failure("Không đọc được nội dung file: " + ex.getMessage());
        }

        try {
            List<ChuKySoDto> signatures = PdfSignatureVerifier.verify(pdfBytes);
            if (signatures.isEmpty()) {
                return Result.failure("Không tìm thấy chữ ký số trong file");
            }
            return Result.success(signatures);
        } catch (Exception ex) {
            log.warn("Xac thuc chu ky that bai cho file {}: {}", file.getId(), ex.getMessage());
            return Result.failure("Xác thực chữ ký thất bại: " + ex.getMessage());
        }
    }
}
