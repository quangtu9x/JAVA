package com.td.infrastructure.documents;

import com.td.application.common.models.Result;
import com.td.application.documents.GetFileUseCase;
import com.td.application.documents.DownloadFileRequest;
import com.td.application.documents.FileDto;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GetFileUseCaseImpl implements GetFileUseCase {

    private final FileMetadataJpaRepository fileMetadataRepo;

    @Override
    public Result<FileDto> execute(DownloadFileRequest request) {
        Optional<FileMetadataEntity> opt = fileMetadataRepo
                .findByIdAndDocumentId(request.getFileId(), request.getDocumentId());
        if (opt.isEmpty()) {
            return Result.failure("File không tìm thấy");
        }
        FileMetadataEntity e = opt.get();
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
                .isPrimary(false)
                .version(1)
                .build());
    }
}
