package com.td.infrastructure.documents;

import com.td.application.common.models.Result;
import com.td.application.documents.UploadFileRequest;
import com.td.application.documents.UploadFileUseCase;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadFileUseCaseImpl implements UploadFileUseCase {

    private final MinioService minioService;
    private final FileMetadataJpaRepository fileMetadataRepo;

    @Override
    public Result<UUID> execute(UploadFileRequest request) {
        UUID fileId = UUID.randomUUID();
        String extension = extractExtension(request.getFileName());
        String storedName = fileId + (extension.isEmpty() ? "" : "." + extension);
        String objectPath = "documents/" + request.getDocumentId() + "/" + storedName;

        minioService.uploadObject(objectPath, request.getFileContent(),
                request.getFileSize() != null ? request.getFileSize() : -1,
                request.getMimeType());

        FileMetadataEntity entity = FileMetadataEntity.builder()
                .id(fileId)
                .documentId(request.getDocumentId())
                .originalFilename(request.getFileName())
                .storedFilename(storedName)
                .filePath(objectPath)
                .fileSize(request.getFileSize() != null ? request.getFileSize() : 0L)
                .contentType(request.getMimeType())
                .fileExtension(extension.isEmpty() ? null : extension)
                .fileCategory("DOCUMENT")
                .bucketName(minioService.getBucketName())
                .uploadedAt(LocalDateTime.now())
                .isPublic(false)
                .description(request.getDescription())
                .build();

        fileMetadataRepo.save(entity);
        log.info("Uploaded file {} for document {}", fileId, request.getDocumentId());
        return Result.success(fileId);
    }

    private String extractExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }
}
