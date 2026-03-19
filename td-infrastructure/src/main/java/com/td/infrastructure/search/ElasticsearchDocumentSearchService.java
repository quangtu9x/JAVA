package com.td.infrastructure.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.td.application.common.models.PaginationResponse;
import com.td.application.documents.DocumentDto;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DocumentSearchService;
import com.td.application.documents.DocumentSearchStatusDto;
import com.td.application.documents.SearchDocumentsRequest;
import com.td.domain.documents.BusinessDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchDocumentSearchService implements DocumentSearchService {

    private static final String BACKEND = "ELASTICSEARCH";

    private final ElasticsearchOperations operations;
    private final DocumentRepository documentRepository;
    private final DocumentSearchProperties properties;

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public boolean isAvailable() {
        if (!isEnabled()) {
            return false;
        }

        try {
            return operations.indexOps(DocumentSearchDocument.class).exists();
        } catch (Exception ex) {
            log.debug("Elasticsearch availability check failed: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public PaginationResponse<DocumentDto> search(SearchDocumentsRequest request) {
        if (!isEnabled()) {
            throw new IllegalStateException("Elasticsearch đang bị tắt bởi cấu hình");
        }

        IndexOperations indexOperations = operations.indexOps(DocumentSearchDocument.class);
        if (!indexOperations.exists()) {
            throw new IllegalStateException("Index Elasticsearch chưa được khởi tạo. Hãy chạy reindex trước");
        }

        Pageable pageable = buildPageable(request);
        Query esQuery = buildQuery(request);
        NativeQuery query = NativeQuery.builder()
            .withQuery(esQuery)
            .withPageable(pageable)
            .build();

        var searchHits = operations.search(query, DocumentSearchDocument.class);
        List<DocumentDto> items = searchHits.getSearchHits().stream()
            .map(SearchHit::getContent)
            .map(DocumentSearchMapper::toDto)
            .toList();

        long totalItems = searchHits.getTotalHits();
        int totalPages = totalItems == 0
            ? 0
            : (int) Math.ceil((double) totalItems / pageable.getPageSize());

        return new PaginationResponse<>(
            items,
            pageable.getPageNumber(),
            pageable.getPageSize(),
            totalItems,
            totalPages,
            pageable.getPageNumber() == 0,
            totalPages == 0 || pageable.getPageNumber() >= totalPages - 1
        );
    }

    @Override
    public long reindexAll() {
        if (!isEnabled()) {
            return 0L;
        }

        try {
            recreateIndex();

            long indexedCount = 0L;
            int pageNumber = 0;
            while (true) {
                SearchDocumentsRequest request = new SearchDocumentsRequest();
                Pageable pageable = PageRequest.of(pageNumber, 200, Sort.by(Sort.Direction.DESC, "lastModifiedOn"));
                var page = documentRepository.search(request, pageable);

                for (BusinessDocument document : page.getContent()) {
                    operations.save(DocumentSearchMapper.toSearchDocument(document));
                    indexedCount++;
                }

                if (page.isLast()) {
                    break;
                }
                pageNumber++;
            }

            operations.indexOps(DocumentSearchDocument.class).refresh();
            return indexedCount;
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể reindex documents lên Elasticsearch: " + ex.getMessage(), ex);
        }
    }

    @Override
    public DocumentSearchStatusDto getStatus() {
        if (!isEnabled()) {
            return DocumentSearchStatusDto.builder()
                .enabled(false)
                .available(false)
                .backend(BACKEND)
                .indexName(DocumentSearchDocument.INDEX_NAME)
                .indexedDocuments(0)
                .message("Elasticsearch đang bị tắt bởi cấu hình app.elasticsearch.enabled=false")
                .build();
        }

        try {
            IndexOperations indexOperations = operations.indexOps(DocumentSearchDocument.class);
            if (!indexOperations.exists()) {
                return DocumentSearchStatusDto.builder()
                    .enabled(true)
                    .available(false)
                    .backend(BACKEND)
                    .indexName(DocumentSearchDocument.INDEX_NAME)
                    .indexedDocuments(0)
                    .message("Cluster Elasticsearch reachable nhưng index chưa được khởi tạo. Hãy chạy reindex")
                    .build();
            }

            NativeQuery countQuery = NativeQuery.builder()
                .withQuery(Query.of(q -> q.matchAll(m -> m)))
                .build();

            long indexedDocuments = operations.count(countQuery, DocumentSearchDocument.class);
            return DocumentSearchStatusDto.builder()
                .enabled(true)
                .available(true)
                .backend(BACKEND)
                .indexName(DocumentSearchDocument.INDEX_NAME)
                .indexedDocuments(indexedDocuments)
                .message("Elasticsearch đang hoạt động bình thường")
                .build();
        } catch (Exception ex) {
            return DocumentSearchStatusDto.builder()
                .enabled(true)
                .available(false)
                .backend(BACKEND)
                .indexName(DocumentSearchDocument.INDEX_NAME)
                .indexedDocuments(0)
                .message("Không thể kết nối Elasticsearch: " + ex.getMessage())
                .build();
        }
    }

    @Override
    public void index(BusinessDocument document) {
        if (!isEnabled() || document == null || document.isDeleted()) {
            return;
        }

        try {
            IndexOperations indexOperations = operations.indexOps(DocumentSearchDocument.class);
            if (!indexOperations.exists()) {
                log.info("Elasticsearch index {} chưa tồn tại, bỏ qua sync từng bản ghi và chờ reindex đầu tiên",
                    DocumentSearchDocument.INDEX_NAME);
                return;
            }

            operations.save(DocumentSearchMapper.toSearchDocument(document));
        } catch (Exception ex) {
            log.warn("Đồng bộ document {} lên Elasticsearch thất bại: {}", document.getId(), ex.getMessage());
        }
    }

    @Override
    public void delete(UUID documentId) {
        if (!isEnabled() || documentId == null) {
            return;
        }

        try {
            IndexOperations indexOperations = operations.indexOps(DocumentSearchDocument.class);
            if (!indexOperations.exists()) {
                return;
            }
            operations.delete(String.valueOf(documentId), DocumentSearchDocument.class);
        } catch (Exception ex) {
            log.warn("Xóa document {} khỏi Elasticsearch thất bại: {}", documentId, ex.getMessage());
        }
    }

    private void recreateIndex() {
        IndexOperations indexOperations = operations.indexOps(DocumentSearchDocument.class);
        if (indexOperations.exists()) {
            indexOperations.delete();
        }
        indexOperations.createWithMapping();
    }

    private Pageable buildPageable(SearchDocumentsRequest request) {
        int pageNumber = request == null ? 0 : Math.max(0, request.getPageNumber());
        int pageSize = request == null ? 10 : Math.min(Math.max(1, request.getPageSize()), 100);
        String sortBy = request == null ? "lastModifiedOn" : request.getSortBy();
        String indexSortField = switch (sortBy == null ? "lastModifiedOn" : sortBy) {
            case "createdOn" -> "createdOnEpochMs";
            case "lastModifiedOn" -> "lastModifiedOnEpochMs";
            case "versionNo" -> "versionNo";
            case "title" -> "title";
            case "documentType" -> "documentType";
            case "status" -> "status";
            default -> "lastModifiedOnEpochMs";
        };

        Sort.Direction direction = request != null && "asc".equalsIgnoreCase(request.getSortDirection())
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;

        return PageRequest.of(pageNumber, pageSize, Sort.by(direction, indexSortField));
    }

    private Query buildQuery(SearchDocumentsRequest request) {
        if (request == null) {
            return Query.of(q -> q.matchAll(m -> m));
        }

        List<Query> mustQueries = new ArrayList<>();
        List<Query> filterQueries = new ArrayList<>();

        if (StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            mustQueries.add(Query.of(q -> q.bool(b -> b
                .should(s -> s.multiMatch(mm -> mm
                    .query(keyword)
                    .fields("title^6", "content^2", "searchText^3")
                    .fuzziness("AUTO")
                    .prefixLength(1)
                    .maxExpansions(50)
                    .minimumShouldMatch("75%")))
                .should(s -> s.matchPhrasePrefix(mpp -> mpp
                    .field("title")
                    .query(keyword)
                    .maxExpansions(25)))
                .should(s -> s.matchPhrasePrefix(mpp -> mpp
                    .field("searchText")
                    .query(keyword)
                    .maxExpansions(25)))
                .minimumShouldMatch("1"))));
        }

        if (StringUtils.hasText(request.getDocumentType())) {
            String documentType = request.getDocumentType().trim().toLowerCase();
            filterQueries.add(Query.of(q -> q.term(t -> t.field("documentType").value(documentType))));
        }

        if (StringUtils.hasText(request.getStatus())) {
            String status = request.getStatus().trim().toLowerCase();
            filterQueries.add(Query.of(q -> q.term(t -> t.field("status").value(status))));
        }

        filterQueries.add(Query.of(q -> q.term(t -> t.field("deleted").value(false))));

        if (mustQueries.isEmpty() && filterQueries.isEmpty()) {
            return Query.of(q -> q.matchAll(m -> m));
        }

        return Query.of(q -> q.bool(b -> {
            mustQueries.forEach(b::must);
            filterQueries.forEach(b::filter);
            return b;
        }));
    }
}
