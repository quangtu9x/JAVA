package com.td.infrastructure.onlyoffice;

import com.td.application.common.models.Result;
import com.td.application.onlyoffice.SaveOnlyOfficeCallbackRequest;
import com.td.application.onlyoffice.SaveOnlyOfficeCallbackUseCase;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Ghi đè tệp tại ĐÚNG {@code file_path}/{@code stored_filename} hiện có (không tạo dòng
 * {@code file_metadata} mới, không đổi {@code fileId}) — xem javadoc
 * {@link com.td.application.onlyoffice.SaveOnlyOfficeCallbackRequest} lý do chọn cách này thay vì
 * tái dùng {@code UploadFileUseCase} (vốn luôn tạo dòng mới).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SaveOnlyOfficeCallbackUseCaseImpl implements SaveOnlyOfficeCallbackUseCase {

    private final FileMetadataJpaRepository fileMetadataRepository;
    private final MinioService minioService;

    @Override
    public Result<Void> execute(SaveOnlyOfficeCallbackRequest request) {
        Optional<FileMetadataEntity> opt = fileMetadataRepository
                .findByIdAndDocumentId(request.getFileId(), request.getDocumentId());
        if (opt.isEmpty()) {
            return Result.failure("File không tồn tại (documentId=" + request.getDocumentId()
                    + ", fileId=" + request.getFileId() + ")");
        }
        FileMetadataEntity entity = opt.get();
        try {
            minioService.uploadObject(entity.getFilePath(), request.getContent(), request.getContentLength(),
                    entity.getContentType());
        } catch (Exception ex) {
            log.error("Luu tep OnlyOffice callback vao MinIO that bai (fileId={})", request.getFileId(), ex);
            return Result.failure("Không thể lưu tệp vào MinIO: " + ex.getMessage());
        }

        entity.setFileSize(request.getContentLength());
        entity.setUploadedAt(LocalDateTime.now());
        fileMetadataRepository.save(entity);
        return Result.success(null);
    }
}
