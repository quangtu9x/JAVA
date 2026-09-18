package com.td.application.documents;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
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
            log.error("Reindex Elasticsearch thất bại", ex);
            return Result.failure("Reindex Elasticsearch thất bại: " + ex.getMessage());
        }
    }
}
