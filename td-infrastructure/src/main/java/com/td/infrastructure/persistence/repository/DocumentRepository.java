package com.td.infrastructure.persistence.repository;

import com.td.application.documents.SearchDocumentsRequest;
import com.td.domain.documents.BusinessDocument;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public abstract class DocumentRepository implements com.td.application.documents.DocumentRepository {

    private final DocumentJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    protected DocumentRepository(DocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<BusinessDocument> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<BusinessDocument, UUID>) jpaRepository).findById(id);
    }

    @Override
    public <S extends BusinessDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public void delete(BusinessDocument entity) {
        jpaRepository.delete(entity);
    }

    @Override
    public Page<BusinessDocument> search(SearchDocumentsRequest request, Pageable pageable) {
        List<DocumentSearchSupport.AttributeFilterRule> filterRules =
            DocumentSearchSupport.parseAttributeFilters(request == null ? null : request.getAttributeFilters());

        if (filterRules.isEmpty()) {
            return DocumentSearchSupport.searchWithJpa(jpaRepository, request, pageable);
        }

        List<BusinessDocument> candidates = findCandidates(request, filterRules);
        return DocumentSearchSupport.filterSortAndPage(candidates, filterRules, pageable);
    }

    protected List<BusinessDocument> findCandidates(
            SearchDocumentsRequest request,
            List<DocumentSearchSupport.AttributeFilterRule> filterRules) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM documents d
            WHERE d.deleted_on IS NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        appendCommonFilters(sql, parameters, request);
        appendAttributeKeyPredicates(sql, parameters, filterRules);

        Query query = entityManager.createNativeQuery(sql.toString(), BusinessDocument.class);
        parameters.forEach(query::setParameter);

        @SuppressWarnings("unchecked")
        List<BusinessDocument> candidates = query.getResultList();
        return candidates;
    }

    protected abstract void appendAttributeKeyPredicates(
            StringBuilder sql,
            Map<String, Object> parameters,
            List<DocumentSearchSupport.AttributeFilterRule> filterRules);

    protected String toJsonPath(String key) {
        String escaped = key == null ? "" : key.replace("\\", "\\\\").replace("\"", "\\\"");
        return "$.\"" + escaped + "\"";
    }

    private void appendCommonFilters(StringBuilder sql, Map<String, Object> parameters, SearchDocumentsRequest request) {
        if (request == null) {
            return;
        }

        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            sql.append("""
                 AND (
                     LOWER(COALESCE(d.title, '')) LIKE :keyword
                     OR LOWER(COALESCE(d.content, '')) LIKE :keyword
                     OR LOWER(COALESCE(d.document_type, '')) LIKE :keyword
                     OR LOWER(COALESCE(d.status, '')) LIKE :keyword
                     OR LOWER(COALESCE(d.tags_json, '')) LIKE :keyword
                     OR LOWER(COALESCE(d.attributes_json, '')) LIKE :keyword
                 )
                """);
            parameters.put("keyword", "%" + request.getKeyword().trim().toLowerCase() + "%");
        }

        if (request.getDocumentType() != null && !request.getDocumentType().isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", request.getDocumentType().trim().toLowerCase());
        }

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.status, '')) = :status");
            parameters.put("status", request.getStatus().trim().toLowerCase());
        }
    }
}
