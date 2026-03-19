package com.td.application.documents;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchDocumentsElasticUseCase {

    private static final String ELASTICSEARCH_BACKEND = "ELASTICSEARCH";
    private static final String DATABASE_BACKEND = "DATABASE";

    private final DocumentSearchService documentSearchService;
    private final SearchDocumentsUseCase searchDocumentsUseCase;

    public DocumentSearchPageResult execute(SearchDocumentsRequest request) {
        if (requiresDatabaseFallback(request)) {
            return new DocumentSearchPageResult(searchDocumentsUseCase.execute(request), DATABASE_BACKEND);
        }

        try {
            return new DocumentSearchPageResult(documentSearchService.search(request), ELASTICSEARCH_BACKEND);
        } catch (Exception ex) {
            return new DocumentSearchPageResult(searchDocumentsUseCase.execute(request), DATABASE_BACKEND);
        }
    }

    private boolean requiresDatabaseFallback(SearchDocumentsRequest request) {
        return hasAttributeFilters(request)
            || !documentSearchService.isEnabled()
            || !documentSearchService.isAvailable();
    }

    private boolean hasAttributeFilters(SearchDocumentsRequest request) {
        return request != null
            && request.getAttributeFilters() != null
            && !request.getAttributeFilters().isEmpty();
    }
}
