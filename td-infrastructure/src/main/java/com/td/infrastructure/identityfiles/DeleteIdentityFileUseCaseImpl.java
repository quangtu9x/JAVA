package com.td.infrastructure.identityfiles;

import com.td.application.common.models.Result;
import com.td.application.documents.DeleteFileRequest;
import com.td.application.identityfiles.DeleteIdentityFileUseCase;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.persistence.identity.IdentityFileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

// "Identity" tương đương DeleteFileUseCaseImpl - xem javadoc IdentityFileMetadata. Hard delete (xoá
// hẳn khỏi DB + MinIO), đúng theo tiền lệ đã có của DeleteFileUseCaseImpl (file_metadata) - tệp vật
// lý không giữ lại bản "soft-deleted" mồ côi trong MinIO.
@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteIdentityFileUseCaseImpl implements DeleteIdentityFileUseCase {

    private final IdentityFileMetadataJpaRepository identityFileMetadataRepo;
    private final MinioService minioService;

    @Override
    public Result<UUID> execute(DeleteFileRequest request) {
        Optional<com.td.domain.sharedcore.IdentityFileMetadata> opt = identityFileMetadataRepo
            .findByIdAndDocumentId(request.getFileId(), request.getDocumentId());
        if (opt.isEmpty()) {
            return Result.failure("File không tìm thấy");
        }
        var entity = opt.get();
        minioService.removeObject(entity.getFilePath());
        identityFileMetadataRepo.delete(entity);
        log.info("Deleted identity file {} from document {}", request.getFileId(), request.getDocumentId());
        return Result.success(request.getFileId());
    }
}
