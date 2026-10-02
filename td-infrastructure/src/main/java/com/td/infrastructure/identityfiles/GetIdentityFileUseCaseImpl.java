package com.td.infrastructure.identityfiles;

import com.td.application.common.models.Result;
import com.td.application.documents.DownloadFileRequest;
import com.td.application.documents.FileDto;
import com.td.application.identityfiles.GetIdentityFileUseCase;
import com.td.infrastructure.persistence.identity.IdentityFileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

// "Identity" tương đương GetFileUseCaseImpl - xem javadoc IdentityFileMetadata.
@Service
@RequiredArgsConstructor
public class GetIdentityFileUseCaseImpl implements GetIdentityFileUseCase {

    private final IdentityFileMetadataJpaRepository identityFileMetadataRepo;

    @Override
    public Result<FileDto> execute(DownloadFileRequest request) {
        Optional<com.td.domain.sharedcore.IdentityFileMetadata> opt = identityFileMetadataRepo
            .findByIdAndDocumentId(request.getFileId(), request.getDocumentId());
        if (opt.isEmpty()) {
            return Result.failure("File không tìm thấy");
        }
        var e = opt.get();
        return Result.success(FileDto.builder()
            .fileId(e.getId())
            .documentId(e.getDocumentId())
            .fileName(e.getStoredFilename())
            .originalFileName(e.getOriginalFilename())
            .fileSize(e.getFileSize())
            .mimeType(e.getContentType())
            .uploadDate(e.getUploadedAt())
            .uploadedBy(e.getUploadedBy() != null ? e.getUploadedBy().toString() : null)
            .storagePath(e.getFilePath())
            .isPrimary(Boolean.TRUE.equals(e.getIsPrimary()))
            .version(1)
            .build());
    }
}
