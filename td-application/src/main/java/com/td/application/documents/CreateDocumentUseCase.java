package com.td.application.documents;

import com.td.application.common.models.Result;
import com.td.domain.documents.BusinessDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CreateDocumentUseCase {

    private final DocumentRepository documentRepository;

    public Result<UUID> execute(CreateDocumentRequest request) {
        try {
            var document = new BusinessDocument(
                request.getTitle().trim(),
                normalize(request.getDocumentType()),
                normalizeDefault(request.getStatus(), "DRAFT"),
                request.getContent(),
                DocumentJsonMapper.toJsonArray(request.getTags()),
                DocumentJsonMapper.toJsonObject(request.getAttributes()),
                DocumentJsonMapper.toJsonObject(request.getMetadata())
            );

            var saved = documentRepository.save(document);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            return Result.failure("Failed to create document: " + ex.getMessage());
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
