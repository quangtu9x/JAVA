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

    public Result<DocumentDto> execute(GetDocumentRequest request) {
        try {
            var documentOptional = documentRepository.findById(request.getId());
            if (documentOptional.isEmpty()) {
                return Result.failure("Document not found with ID: " + request.getId());
            }

            var document = documentOptional.get();
            if (document.isDeleted()) {
                return Result.failure("Document was deleted");
            }

            return Result.success(DocumentDtoMapper.map(document));
        } catch (Exception ex) {
            return Result.failure("Failed to get document: " + ex.getMessage());
        }
    }
}