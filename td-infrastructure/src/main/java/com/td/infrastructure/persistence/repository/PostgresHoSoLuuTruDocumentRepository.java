package com.td.infrastructure.persistence.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.hosoluutru.HoSoLuuTruDocumentRepository;
import com.td.domain.hosoluutru.HoSoLuuTruDocument;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Port module "Hồ sơ lưu trữ" — adapter cho bảng vật lý ĐỘC LẬP {@code hosoluutru_documents}. Copy CÓ
 * CHỦ Ý phần predicate SQL (containment/status IN) từ {@code PostgresHoSoCVDocumentRepository} — chỉ
 * đổi tên bảng/entity + gộp lại còn 1 method tổ hợp {@link #findByCriteria} (xem javadoc
 * {@link HoSoLuuTruDocumentRepository}).
 */
@Repository
public class PostgresHoSoLuuTruDocumentRepository implements HoSoLuuTruDocumentRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String STATUS_FIELD_NAME = "status";

    private final HoSoLuuTruDocumentJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresHoSoLuuTruDocumentRepository(HoSoLuuTruDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<HoSoLuuTruDocument> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<HoSoLuuTruDocument, UUID>) jpaRepository)
            .findById(id);
    }

    @Override
    public <S extends HoSoLuuTruDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public void delete(HoSoLuuTruDocument entity) {
        jpaRepository.delete(entity);
    }

    @Override
    public Page<HoSoLuuTruDocument> findByCriteria(
            String documentType,
            List<String> matchAnyFieldNames, String matchValue,
            Map<String, List<String>> fieldValueIn,
            Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM hosoluutru_documents d
            WHERE d.deleted_on IS NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        if (matchAnyFieldNames != null && !matchAnyFieldNames.isEmpty()) {
            appendAnyFieldContainsPredicate(sql, parameters, matchAnyFieldNames, matchValue);
        }
        appendFieldValueInPredicate(sql, parameters, fieldValueIn);
        appendDocumentTypeFilter(sql, parameters, documentType);

        return executeQuery(sql, parameters, pageable, "d.last_modified_on DESC NULLS LAST, d.created_on DESC");
    }

    @Override
    public Page<HoSoLuuTruDocument> findDeleted(String documentType, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM hosoluutru_documents d
            WHERE d.deleted_on IS NOT NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        appendDocumentTypeFilter(sql, parameters, documentType);

        return executeQuery(sql, parameters, pageable, "d.deleted_on DESC NULLS LAST");
    }

    // ---- helpers (copy tu PostgresHoSoCVDocumentRepository, chi doi ten bang) ----

    private Page<HoSoLuuTruDocument> executeQuery(
            StringBuilder sql, Map<String, Object> parameters, Pageable pageable, String orderBy) {
        long total = countNative(sql.toString(), parameters);

        sql.append(" ORDER BY ").append(orderBy).append(" LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), HoSoLuuTruDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<HoSoLuuTruDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    private void appendDocumentTypeFilter(StringBuilder sql, Map<String, Object> parameters, String documentType) {
        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }
    }

    void appendAnyFieldContainsPredicate(
            StringBuilder sql, Map<String, Object> parameters, List<String> fieldNames, String value) {
        if (fieldNames == null || fieldNames.isEmpty()) {
            sql.append(" AND 1 = 0");
            return;
        }
        sql.append(" AND (");
        for (int index = 0; index < fieldNames.size(); index++) {
            if (index > 0) {
                sql.append(" OR ");
            }
            String containsParam = "containsField" + index;
            String scalarParam = "containsFieldScalar" + index;
            String fieldName = fieldNames.get(index);
            sql.append("(d.dynamic_fields @> CAST(:").append(containsParam).append(" AS jsonb)")
                .append(" OR COALESCE(d.dynamic_fields->>'").append(fieldName).append("', '') = :")
                .append(scalarParam).append(")");
            parameters.put(containsParam, buildContainsFilter(fieldName, value));
            parameters.put(scalarParam, value == null ? "" : value);
        }
        sql.append(")");
    }

    private String buildContainsFilter(String fieldName, String value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(fieldName, List.of(value)));
        } catch (Exception ex) {
            return "{}";
        }
    }

    // Package-private (khong private) de unit-test truc tiep SQL builder khong can EntityManager.
    void appendFieldValueInPredicate(
            StringBuilder sql, Map<String, Object> parameters, Map<String, List<String>> fieldValueIn) {
        if (fieldValueIn == null || fieldValueIn.isEmpty()) {
            return;
        }
        int fieldIndex = 0;
        for (Map.Entry<String, List<String>> entry : fieldValueIn.entrySet()) {
            List<String> allowedValues = entry.getValue();
            if (allowedValues == null || allowedValues.isEmpty()) {
                continue;
            }
            String column = STATUS_FIELD_NAME.equals(entry.getKey())
                ? "LOWER(COALESCE(d.status, ''))"
                : "LOWER(COALESCE(d.dynamic_fields->>'" + entry.getKey() + "', ''))";
            List<String> paramRefs = new ArrayList<>();
            String prefix = "fieldValueIn" + (fieldIndex++);
            for (int valueIndex = 0; valueIndex < allowedValues.size(); valueIndex++) {
                String parameterName = prefix + "_" + valueIndex;
                paramRefs.add(":" + parameterName);
                String allowedValue = allowedValues.get(valueIndex);
                parameters.put(parameterName, allowedValue == null ? "" : allowedValue.trim().toLowerCase());
            }
            sql.append(" AND ").append(column).append(" IN (").append(String.join(",", paramRefs)).append(")");
        }
    }

    private long countNative(String sql, Map<String, Object> parameters) {
        Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM (" + sql + ") AS matched");
        parameters.forEach(countQuery::setParameter);
        return ((Number) countQuery.getSingleResult()).longValue();
    }
}
