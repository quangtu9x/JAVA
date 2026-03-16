package com.td.application.documents;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UpdateDocumentUseCase {

    private final DocumentRepository documentRepository;

    public Result<UUID> execute(UpdateDocumentRequest request) {
        try {
            var documentOptional = documentRepository.findById(request.getId());
            if (documentOptional.isEmpty()) {
                return Result.failure("Document not found with ID: " + request.getId());
            }

            var document = documentOptional.get();
            if (document.isDeleted()) {
                return Result.failure("Document was deleted and cannot be updated");
            }

            document.update(
                request.getTitle().trim(),
                normalize(request.getDocumentType()),
                normalize(request.getStatus()),
                request.getContent(),
                DocumentJsonMapper.toJsonArray(request.getTags()),
                DocumentJsonMapper.toJsonObject(request.getAttributes()),
                DocumentJsonMapper.toJsonObject(request.getMetadata())
            );

            var saved = documentRepository.save(document);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            return Result.failure("Failed to update document: " + ex.getMessage());
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
