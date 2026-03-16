package com.td.application.documents;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DeleteDocumentUseCase {

    private final DocumentRepository documentRepository;

    public Result<UUID> execute(UUID documentId) {
        try {
            var documentOptional = documentRepository.findById(documentId);
            if (documentOptional.isEmpty()) {
                return Result.failure("Document not found with ID: " + documentId);
            }

            var document = documentOptional.get();
            if (document.isDeleted()) {
                return Result.failure("Document already deleted");
            }

            // TODO: Replace random UUID with authenticated user ID when user context is available.
            document.markAsDeleted(UUID.randomUUID());
            var saved = documentRepository.save(document);

            return Result.success(saved.getId());
        } catch (Exception ex) {
            return Result.failure("Failed to delete document: " + ex.getMessage());
        }
    }
}
