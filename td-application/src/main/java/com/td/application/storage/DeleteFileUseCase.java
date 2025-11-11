package com.td.application.storage;

import com.td.application.common.cqrs.UseCase;
import com.td.domain.storage.FileMetadata;
import com.td.domain.storage.FileStorageRepository;
import com.td.infrastructure.storage.MinIOService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteFileUseCase implements UseCase<UUID, Boolean> {

    private final MinIOService minIOService;
    private final FileStorageRepository fileStorageRepository;

    @Override
    @Transactional
    public Boolean execute(UUID fileId) {
        try {
            // Get file metadata
            FileMetadata fileMetadata = fileStorageRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found with ID: " + fileId));
            
            // Delete from MinIO
            minIOService.deleteFile(fileMetadata.getFilePath());
            
            // Delete metadata from database
            fileStorageRepository.delete(fileMetadata);
            
            log.info("File deleted successfully: {} (ID: {})", 
                fileMetadata.getOriginalFilename(), fileId);
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to delete file with ID: {}", fileId, e);
            return false;
        }
    }
}