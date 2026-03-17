package com.td.application.storage;

import com.td.application.common.cqrs.UseCase;
import com.td.domain.storage.FileMetadata;
import com.td.domain.storage.FileStorageRepository;
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
            // Lấy thông tin file
            FileMetadata fileMetadata = fileStorageRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy file với ID: " + fileId));
            
            // Xóa khỏi MinIO
            minIOService.deleteFile(fileMetadata.getFilePath());
            
            // Xóa metadata khỏi database
            fileStorageRepository.delete(fileMetadata);
            
            log.info("Đã xóa file thành công: {} (ID: {})", 
                fileMetadata.getOriginalFilename(), fileId);
            
            return true;
            
        } catch (Exception e) {
            log.error("Xóa file thất bại với ID: {}", fileId, e);
            return false;
        }
    }
}
