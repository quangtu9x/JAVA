package com.td.infrastructure.documents;

import com.td.application.documents.FileDto;
import com.td.application.documents.ListFilesForDocumentsUseCase;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Xem javadoc {@link ListFilesForDocumentsUseCase}. Cùng công thức map field với
 * {@link ListDocumentFilesUseCaseImpl} (1 document) — copy y hệt cho nhất quán, không tái dùng trực
 * tiếp vì shape input/output khác nhau (1 document vs nhiều document, gom theo document_id).
 */
@Service
@RequiredArgsConstructor
public class ListFilesForDocumentsUseCaseImpl implements ListFilesForDocumentsUseCase {

    private final FileMetadataJpaRepository fileMetadataRepo;

    @Override
    public Map<UUID, List<FileDto>> execute(List<UUID> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Map.of();
        }
        List<FileMetadataEntity> entities = fileMetadataRepo.findAllByDocumentIdIn(documentIds);
        return entities.stream()
            .map(this::toFileDto)
            .collect(Collectors.groupingBy(FileDto::getDocumentId));
    }

    private FileDto toFileDto(FileMetadataEntity e) {
        return FileDto.builder()
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
            .build();
    }
}
