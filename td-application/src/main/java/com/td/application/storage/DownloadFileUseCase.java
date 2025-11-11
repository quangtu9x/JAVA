package com.td.application.storage;

import com.td.application.common.cqrs.UseCase;
import com.td.domain.storage.FileMetadata;
import com.td.domain.storage.FileStorageRepository;
import com.td.infrastructure.storage.MinIOService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadFileUseCase implements UseCase<UUID, DownloadFileResponse> {

    private final MinIOService minIOService;
    private final FileStorageRepository fileStorageRepository;

    @Override
    @Transactional
    public DownloadFileResponse execute(UUID fileId) {
        try {
            // Get file metadata
            FileMetadata fileMetadata = fileStorageRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found with ID: " + fileId));
            
            // Download from MinIO
            InputStream inputStream = minIOService.downloadFile(fileMetadata.getFilePath());
            
            // Update download statistics
            fileMetadata.incrementDownloadCount();
            fileStorageRepository.save(fileMetadata);
            
            log.info("File downloaded successfully: {} (ID: {})", 
                fileMetadata.getOriginalFilename(), fileId);
            
            return new DownloadFileResponse(
                inputStream,
                fileMetadata.getOriginalFilename(),
                fileMetadata.getContentType(),
                fileMetadata.getFileSize()
            );
            
        } catch (Exception e) {
            log.error("Failed to download file with ID: {}", fileId, e);
            throw new RuntimeException("Failed to download file: " + e.getMessage(), e);
        }
    }
}