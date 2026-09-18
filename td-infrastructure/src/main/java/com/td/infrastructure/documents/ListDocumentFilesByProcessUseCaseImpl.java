package com.td.infrastructure.documents;

import com.td.application.common.models.PaginationResponse;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesByProcessUseCase;
import com.td.application.documents.ListFilesByProcessRequest;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DT-007 (27/08/2026) — xem javadoc {@link ListDocumentFilesByProcessUseCase}. Cùng công thức map
 * {@code FileMetadataEntity -> FileDto} với {@link ListDocumentFilesUseCaseImpl}, chỉ khác truy
 * vấn (thêm điều kiện {@code process_id}).
 */
@Service
@RequiredArgsConstructor
public class ListDocumentFilesByProcessUseCaseImpl implements ListDocumentFilesByProcessUseCase {

    private final FileMetadataJpaRepository fileMetadataRepo;

    @Override
    public PaginationResponse<FileDto> execute(ListFilesByProcessRequest request) {
        List<FileMetadataEntity> entities = fileMetadataRepo.findAllByDocumentIdAndProcessId(
            request.getDocumentId(), request.getProcessId());
        List<FileDto> items = entities.stream().map(e -> FileDto.builder()
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
                .build()
        ).collect(Collectors.toList());

        return new PaginationResponse<>(items, 0, items.size(), (long) items.size(), 1, true, true);
    }
}
