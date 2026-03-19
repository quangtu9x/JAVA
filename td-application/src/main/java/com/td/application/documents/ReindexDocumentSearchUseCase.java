package com.td.application.documents;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReindexDocumentSearchUseCase {

    private final DocumentSearchService documentSearchService;

    public Result<Long> execute() {
        try {
            if (!documentSearchService.isEnabled()) {
                return Result.failure("Elasticsearch đang bị tắt bởi cấu hình app.elasticsearch.enabled=false");
            }

            long indexedCount = documentSearchService.reindexAll();
            return Result.success(indexedCount);
        } catch (Exception ex) {
            return Result.failure("Reindex Elasticsearch thất bại: " + ex.getMessage());
        }
    }
}
