package com.td.application.documents;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetDocumentUseCase {

    private final DocumentRepository documentRepository;
    private final DocumentCacheService documentCacheService;

    public Result<DocumentDto> execute(GetDocumentRequest request) {
        try {
            if (request == null || request.getId() == null) {
                return Result.failure("Document ID is required");
            }

            var cachedDocument = documentCacheService.get(request.getId());
            if (cachedDocument != null) {
                return Result.success(cachedDocument);
            }

            var documentOptional = documentRepository.findById(request.getId());
            if (documentOptional.isEmpty()) {
                return Result.failure("Document not found with ID: " + request.getId());
            }

            var document = documentOptional.get();
            if (document.isDeleted()) {
                documentCacheService.evict(request.getId());
                return Result.failure("Document was deleted");
            }

            var mappedDocument = DocumentDtoMapper.map(document);
            documentCacheService.put(request.getId(), mappedDocument);
            return Result.success(mappedDocument);
        } catch (Exception ex) {
            return Result.failure("Failed to get document: " + ex.getMessage());
        }
    }
}