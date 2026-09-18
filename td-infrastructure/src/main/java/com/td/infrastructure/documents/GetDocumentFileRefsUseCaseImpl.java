package com.td.infrastructure.documents;

import com.td.application.documents.GetDocumentFileRefsUseCase;
import com.td.application.documents.VbdiDeliveryFileRef;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Xem javadoc {@link GetDocumentFileRefsUseCase}. */
@Service
@RequiredArgsConstructor
public class GetDocumentFileRefsUseCaseImpl implements GetDocumentFileRefsUseCase {

    private final FileMetadataJpaRepository fileMetadataRepo;

    @Override
    public List<VbdiDeliveryFileRef> execute(UUID documentId) {
        if (documentId == null) {
            return List.of();
        }
        return fileMetadataRepo.findAllByDocumentId(documentId).stream()
            .map(e -> VbdiDeliveryFileRef.builder()
                .objectKey(e.getFilePath())
                .bucketName(e.getBucketName())
                .originalFilename(e.getOriginalFilename())
                .size(e.getFileSize())
                .contentType(e.getContentType())
                .fileExtension(e.getFileExtension())
                .build())
            .toList();
    }
}
