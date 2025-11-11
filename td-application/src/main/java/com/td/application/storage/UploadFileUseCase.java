package com.td.application.storage;

import com.td.application.common.cqrs.UseCase;
import com.td.domain.storage.FileMetadata;
import com.td.domain.storage.FileStorageRepository;
import com.td.infrastructure.storage.MinIOService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadFileUseCase implements UseCase<UploadFileRequest, UploadFileResponse> {

    private final MinIOService minIOService;
    private final FileStorageRepository fileStorageRepository;
    private final FileMetadataMapper fileMetadataMapper;

    @Override
    @Transactional
    public UploadFileResponse execute(UploadFileRequest request) {
        try {
            // Upload to MinIO
            FileMetadata fileMetadata = minIOService.uploadFile(
                request.getFile(),
                request.getFileCategory(),
                request.getUploadedBy(),
                request.getDescription()
            );
            
            // Set additional properties
            fileMetadata.setIsPublic(request.getIsPublic());
            fileMetadata.setTags(request.getTags());
            
            // Save metadata to database
            FileMetadata savedMetadata = fileStorageRepository.save(fileMetadata);
            
            // Generate download URL
            String downloadUrl = minIOService.generatePresignedDownloadUrl(
                savedMetadata.getFilePath(), 60); // 60 minutes expiry
            
            // Convert to DTO
            FileMetadataDto dto = fileMetadataMapper.toDto(savedMetadata);
            dto.setDownloadUrl(downloadUrl);
            
            log.info("File uploaded successfully: {} (ID: {})", 
                savedMetadata.getOriginalFilename(), savedMetadata.getId());
            
            return UploadFileResponse.success(dto, downloadUrl);
            
        } catch (Exception e) {
            log.error("Failed to upload file: {}", request.getFile().getOriginalFilename(), e);
            return UploadFileResponse.failure("Failed to upload file: " + e.getMessage());
        }
    }
}