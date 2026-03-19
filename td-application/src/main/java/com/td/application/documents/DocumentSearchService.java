package com.td.application.documents;

import com.td.application.common.models.PaginationResponse;
import com.td.domain.documents.BusinessDocument;

import java.util.UUID;

public interface DocumentSearchService {

    boolean isEnabled();

    boolean isAvailable();

    PaginationResponse<DocumentDto> search(SearchDocumentsRequest request);

    long reindexAll();

    DocumentSearchStatusDto getStatus();

    void index(BusinessDocument document);

    void delete(UUID documentId);
}
