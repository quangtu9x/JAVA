package com.td.infrastructure.identityfiles;

import com.td.application.common.models.PaginationResponse;
import com.td.application.documents.FileDto;
import com.td.application.identityfiles.ListIdentityFilesUseCase;
import com.td.infrastructure.persistence.identity.IdentityFileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

// "Identity" tương đương ListDocumentFilesUseCaseImpl - xem javadoc IdentityFileMetadata.
@Service
@RequiredArgsConstructor
public class ListIdentityFilesUseCaseImpl implements ListIdentityFilesUseCase {

    private final IdentityFileMetadataJpaRepository identityFileMetadataRepo;

    @Override
    public PaginationResponse<FileDto> execute(UUID documentId) {
        List<FileDto> items = identityFileMetadataRepo.findAllByDocumentId(documentId).stream()
            .map(e -> FileDto.builder()
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
                .build())
            .collect(Collectors.toList());

        return new PaginationResponse<>(items, 0, items.size(), (long) items.size(), 1, true, true);
    }
}
