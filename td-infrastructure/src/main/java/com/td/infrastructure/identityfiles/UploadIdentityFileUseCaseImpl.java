package com.td.infrastructure.identityfiles;

import com.td.application.common.models.Result;
import com.td.application.documents.UploadFileRequest;
import com.td.application.identityfiles.UploadIdentityFileUseCase;
import com.td.domain.sharedcore.IdentityFileMetadata;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.persistence.identity.IdentityFileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// "Identity" tương đương UploadFileUseCaseImpl (com.td.infrastructure.documents) - xem javadoc
// IdentityFileMetadata cho lý do tồn tại. Khác biệt CỐ Ý duy nhất so với bản gốc: object path KHÔNG
// bao giờ chèn TenantContext.getCurrentTenant() (luôn cố định "identity") - đây CHÍNH LÀ điểm bug cũ
// (tenant của người ĐANG gọi request, không phải tenant "sở hữu" identity) đã gây ra, nên cố tình bỏ
// hẳn, không phải thiếu sót. Không có JSONB sync/edit-history/cache-evict (đặc thù module vbdi/vbden/
// duthao, không áp dụng cho tệp gắn theo danh tính).
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadIdentityFileUseCaseImpl implements UploadIdentityFileUseCase {

    private static final String FIXED_STORAGE_SEGMENT = "identity";
    private static final String DEFAULT_MODULE = "identity";

    private final MinioService minioService;
    private final IdentityFileMetadataJpaRepository identityFileMetadataRepo;

    @Override
    public Result<UUID> execute(UploadFileRequest request) {
        UUID fileId = UUID.randomUUID();
        String extension = extractExtension(request.getFileName());
        String storedName = fileId + (extension.isEmpty() ? "" : "." + extension);
        String moduleSegment = safeSegment(request.getModule(), DEFAULT_MODULE);
        String objectPath = FIXED_STORAGE_SEGMENT + "/" + moduleSegment + "/" + request.getDocumentId() + "/" + storedName;

        minioService.uploadObject(objectPath, request.getFileContent(),
            request.getFileSize() != null ? request.getFileSize() : -1,
            request.getMimeType());

        boolean makePrimary = Boolean.TRUE.equals(request.getIsPrimary());
        if (makePrimary) {
            demoteExistingPrimary(request.getDocumentId());
        }

        IdentityFileMetadata entity = IdentityFileMetadata.builder()
            .id(fileId)
            .documentId(request.getDocumentId())
            .module(moduleSegment)
            .originalFilename(request.getFileName())
            .storedFilename(storedName)
            .filePath(objectPath)
            .fileSize(request.getFileSize() != null ? request.getFileSize() : 0L)
            .contentType(request.getMimeType())
            .fileExtension(extension.isEmpty() ? null : extension)
            .bucketName(minioService.getBucketName())
            .uploadedAt(LocalDateTime.now())
            .isPrimary(makePrimary)
            .description(request.getDescription())
            .build();

        identityFileMetadataRepo.save(entity);
        log.info("Uploaded identity file {} for document {}", fileId, request.getDocumentId());
        return Result.success(fileId);
    }

    private void demoteExistingPrimary(UUID documentId) {
        List<IdentityFileMetadata> currentPrimaries = identityFileMetadataRepo.findAllByDocumentIdAndIsPrimaryTrue(documentId);
        currentPrimaries.forEach(entity -> entity.setIsPrimary(Boolean.FALSE));
        identityFileMetadataRepo.saveAll(currentPrimaries);
    }

    private String safeSegment(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private String extractExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }
}
