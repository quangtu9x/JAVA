package com.td.application.storage;

import com.td.application.common.cqrs.UseCase;
import com.td.domain.storage.FileMetadata;
import com.td.domain.storage.FileStorageRepository;
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
            // Tải lên MinIO
            FileMetadata fileMetadata = minIOService.uploadFile(
                request.getFile(),
                request.getFileCategory(),
                request.getUploadedBy(),
                request.getDescription()
            );
            
            // Cài đặt các thuộc tính bổ sung
            fileMetadata.setIsPublic(request.getIsPublic());
            fileMetadata.setTags(request.getTags());
            
            // Lưu metadata vào database
            FileMetadata savedMetadata = fileStorageRepository.save(fileMetadata);
            
            // Tạo URL tải xuống
            String downloadUrl = minIOService.generatePresignedDownloadUrl(
                savedMetadata.getFilePath(), 60); // hết hạn sau 60 phút
            
            // Chuyển sang DTO
            FileMetadataDto dto = fileMetadataMapper.toDto(savedMetadata);
            dto.setDownloadUrl(downloadUrl);
            
            log.info("Đã tải file lên thành công: {} (ID: {})", 
                savedMetadata.getOriginalFilename(), savedMetadata.getId());
            
            return UploadFileResponse.success(dto, downloadUrl);
            
        } catch (Exception e) {
            log.error("Tải file lên thất bại: {}", request.getFile().getOriginalFilename(), e);
            return UploadFileResponse.failure("Tải file lên thất bại: " + e.getMessage());
        }
    }
}