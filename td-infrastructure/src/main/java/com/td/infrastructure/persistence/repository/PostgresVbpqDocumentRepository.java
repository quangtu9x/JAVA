package com.td.infrastructure.persistence.repository;

import com.td.application.vanbanphapquy.VbpqDocumentRepository;
import com.td.domain.vanbanphapquy.VbpqDocument;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 30/10/2026 (port vanbanphapquy): adapter {@code vbpq_documents} - SQL native, chỉ bản ghi chưa xoá. */
@Repository
public class PostgresVbpqDocumentRepository implements VbpqDocumentRepository {

    private static final String KEYWORD_FIELDS = "d.title || ' ' || COALESCE(d.dynamic_fields->>'sokyhieu', '')"
        + " || ' ' || COALESCE(d.dynamic_fields->>'trichyeu', '') || ' ' || COALESCE(d.dynamic_fields->>'coquanbanhanh', '')"
        + " || ' ' || COALESCE(d.dynamic_fields->>'nguoiky', '') || ' ' || COALESCE(d.dynamic_fields->>'ghichu', '')";

    private final VbpqDocumentJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresVbpqDocumentRepository(VbpqDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<VbpqDocument> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<VbpqDocument, UUID>) jpaRepository).findById(id);
    }

    @Override
    public <S extends VbpqDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public void delete(VbpqDocument entity) {
        jpaRepository.delete(entity);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Page<VbpqDocument> search(String coquanIdentifier, String year, Map<String, String> equalsFields,
                                     Map<String, String> containsFields, String keyword, Pageable pageable) {
        StringBuilder sql = new StringBuilder("SELECT d.* FROM vbpq_documents d WHERE d.deleted_on IS NULL");
        Map<String, Object> params = new LinkedHashMap<>();
        if (coquanIdentifier != null && !coquanIdentifier.isBlank()) {
            sql.append(" AND UPPER(COALESCE(d.coquan_identifier, '')) = :coquan");
            params.put("coquan", coquanIdentifier.trim().toUpperCase());
        }
        if (year != null && !year.isBlank()) {
            // ngayky_date lưu dd/MM/yyyy (có thể kèm giờ) - lấy năm ở phần ngày.
            sql.append(" AND split_part(split_part(COALESCE(d.dynamic_fields->>'ngayky_date', ''), ' ', 1), '/', 3) = :year");
            params.put("year", year.trim());
        }
        int index = 0;
        if (equalsFields != null) {
            for (var entry : equalsFields.entrySet()) {
                if (!KeywordSql.isSafeFieldName(entry.getKey()) || entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }
                String param = "eq" + index++;
                sql.append(" AND LOWER(TRIM(COALESCE(d.dynamic_fields->>'").append(entry.getKey())
                    .append("', ''))) = :").append(param);
                params.put(param, entry.getValue().trim().toLowerCase());
            }
        }
        if (containsFields != null) {
            for (var entry : containsFields.entrySet()) {
                if (!KeywordSql.isSafeFieldName(entry.getKey()) || entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }
                String param = "ct" + index++;
                sql.append(" AND td_search_norm(COALESCE(d.dynamic_fields->>'").append(entry.getKey())
                    .append("', '')) LIKE :").append(param);
                params.put(param, SearchTextSql.containsLike(entry.getValue()));
            }
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND td_search_norm(").append(KEYWORD_FIELDS).append(") LIKE :keyword");
            params.put("keyword", SearchTextSql.containsLike(keyword));
        }
        Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM (" + sql + ") AS matched");
        params.forEach(countQuery::setParameter);
        long total = ((Number) countQuery.getSingleResult()).longValue();
        Query query = entityManager.createNativeQuery(
            sql + " ORDER BY d.created_on DESC NULLS LAST LIMIT :limit OFFSET :offset", VbpqDocument.class);
        params.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());
        return new PageImpl<>(query.getResultList(), pageable, total);
    }
}
