package com.td.infrastructure.documents;

import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DeleteFileRequest;
import com.td.application.documents.DeleteFileUseCase;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteFileUseCaseImpl implements DeleteFileUseCase {

    private final FileMetadataJpaRepository fileMetadataRepo;
    private final MinioService minioService;
    private final DocumentCacheService documentCacheService;

    @Override
    public Result<UUID> execute(DeleteFileRequest request) {
        Optional<FileMetadataEntity> opt = fileMetadataRepo
                .findByIdAndDocumentId(request.getFileId(), request.getDocumentId());
        if (opt.isEmpty()) {
            return Result.failure("File không tìm thấy");
        }
        FileMetadataEntity entity = opt.get();
        minioService.removeObject(entity.getFilePath());
        fileMetadataRepo.delete(entity);
        documentCacheService.evictAllListCaches();
        log.info("Deleted file {} from document {}", request.getFileId(), request.getDocumentId());
        return Result.success(request.getFileId());
    }
}
