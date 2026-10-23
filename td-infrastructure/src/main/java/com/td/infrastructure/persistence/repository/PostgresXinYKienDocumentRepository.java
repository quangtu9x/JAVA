package com.td.infrastructure.persistence.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.documents.VbdenOrgViewFilter;
import com.td.application.vbden.XinYKienDocumentRepository;
import com.td.domain.vbden.XinYKienDocument;
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
 * N08 ("cho ý kiến"), yêu cầu người dùng 31/08/2026 ("tạo bảng riêng"): adapter cho bảng vật lý ĐỘC
 * LẬP {@code vbden_xinykien_documents} (xem javadoc {@link XinYKienDocument}) — KHÔNG dùng lại
 * {@code PostgresDocumentRepository} (query cứng "documents"). Copy CÓ CHỦ Ý phần predicate SQL
 * (containment/status IN/keyword) từ {@code PostgresDuThaoDocumentRepository} (đã qua nhiều bugfix
 * thực tế trong phiên vbden/vbdi/duthao) chỉ đổi tên bảng/entity — KHÔNG viết lại thuật toán.
 */
@Repository
public class PostgresXinYKienDocumentRepository implements XinYKienDocumentRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String STATUS_FIELD_NAME = "status";

    private final XinYKienDocumentJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresXinYKienDocumentRepository(XinYKienDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<XinYKienDocument> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<XinYKienDocument, UUID>) jpaRepository).findById(id);
    }

    @Override
    public <S extends XinYKienDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public void delete(XinYKienDocument entity) {
        jpaRepository.delete(entity);
    }

    @Override
    public Page<XinYKienDocument> findByAnyFieldContains(
            String documentType, List<String> fieldNames, String value, Pageable pageable) {
        return findByAnyFieldContains(documentType, fieldNames, value, null, null, pageable);
    }

    @Override
    public Page<XinYKienDocument> findByAnyFieldContains(
            String documentType, List<String> fieldNames, String value,
            String keyword, List<String> keywordColumns, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbden_xinykien_documents d
            WHERE d.deleted_on IS NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        appendAnyFieldContainsPredicate(sql, parameters, fieldNames, value);
        appendKeywordPredicate(sql, parameters, keyword, keywordColumns);

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }

        long total = countNative(sql.toString(), parameters);

        sql.append(" ORDER BY d.created_on ASC NULLS LAST LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), XinYKienDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<XinYKienDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<XinYKienDocument> findByVbdenOrgView(
            String documentType, VbdenOrgViewFilter filter, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbden_xinykien_documents d
            WHERE d.deleted_on IS NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        appendVbdenOrgKeyPredicates(sql, parameters, filter);
        appendStatusPredicate(sql, parameters, filter.getIncludeFieldValues(), true);
        appendStatusPredicate(sql, parameters, filter.getExcludeFieldValues(), false);
        appendDynamicFieldValuePredicates(sql, parameters, filter.getIncludeFieldValues(), true);
        appendDynamicFieldValuePredicates(sql, parameters, filter.getExcludeFieldValues(), false);
        // 19/10/2026: bộ lọc nâng cao vbden áp cả cho 14 view xin ý kiến (ViewQueryService#withAdvancedSearch).
        TextContainsSql.appendContainsAny(sql, parameters, filter.getContainsAnyFieldValues(), "xyAny");
        TextContainsSql.append(sql, parameters, filter.getTextContainsFieldValues(), false);
        if (filter.getRequireEmptyFieldName() != null && !filter.getRequireEmptyFieldName().isBlank()) {
            sql.append(" AND COALESCE(d.dynamic_fields->>'")
                .append(filter.getRequireEmptyFieldName()).append("', '') = ''");
        }
        if (filter.getRequireNonEmptyFieldName() != null && !filter.getRequireNonEmptyFieldName().isBlank()) {
            sql.append(" AND COALESCE(d.dynamic_fields->>'")
                .append(filter.getRequireNonEmptyFieldName()).append("', '') <> ''");
        }
        appendKeywordPredicate(sql, parameters, filter.getKeyword(), filter.getKeywordColumns());

        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }

        long total = countNative(sql.toString(), parameters);

        sql.append(" ORDER BY d.last_modified_on DESC NULLS LAST LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), XinYKienDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<XinYKienDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    // Copy dung y het PostgresDuThaoDocumentRepository#appendVbdenOrgKeyPredicates.
    void appendVbdenOrgKeyPredicates(
            StringBuilder sql, Map<String, Object> parameters, VbdenOrgViewFilter filter) {
        boolean hasOrgFields = filter.getOrgFieldNames() != null && !filter.getOrgFieldNames().isEmpty();
        boolean hasOrgListFields =
            filter.getOrgListFieldNames() != null && !filter.getOrgListFieldNames().isEmpty();
        if (hasOrgFields) {
            appendAnyFieldContainsPredicate(sql, parameters, filter.getOrgFieldNames(), filter.getOrgValue());
        }
        if (hasOrgListFields) {
            appendAnyListFieldMemberPredicate(sql, parameters, filter.getOrgListFieldNames(), filter.getOrgValue());
        }
        if (!hasOrgFields && !hasOrgListFields) {
            sql.append(" AND 1 = 0");
        }
    }

    // Copy dung y het PostgresDocumentRepository#appendAnyFieldContainsPredicate (bugfix 21/08/2026)
    // - OR containment (JSON array) + scalar-equality cho MOI field.
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
                .append(scalarParam);
            // 27/09/2026: + khop thanh vien chuoi "a;b" - xem javadoc DelimitedMemberSql.
            DelimitedMemberSql.appendOrMember(sql, parameters, fieldName, value, "containsFieldMember" + index);
            sql.append(")");
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

    // Copy dung y het DocumentRepository#appendAnyListFieldMemberPredicate - so khop THANH VIEN cua
    // field dong dang chuoi ";"-joined luu scalar.
    void appendAnyListFieldMemberPredicate(
            StringBuilder sql, Map<String, Object> parameters, List<String> fieldNames, String value) {
        if (fieldNames == null || fieldNames.isEmpty()) {
            sql.append(" AND 1 = 0");
            return;
        }
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (normalized.isEmpty()) {
            sql.append(" AND 1 = 0");
            return;
        }
        sql.append(" AND (");
        for (int index = 0; index < fieldNames.size(); index++) {
            if (index > 0) {
                sql.append(" OR ");
            }
            String fieldName = fieldNames.get(index);
            String column = "LOWER(COALESCE(d.dynamic_fields->>'" + fieldName + "', ''))";
            String exactParam = "listFieldExact" + index;
            String prefixParam = "listFieldPrefix" + index;
            String suffixParam = "listFieldSuffix" + index;
            String middleParam = "listFieldMiddle" + index;
            sql.append("(").append(column).append(" = :").append(exactParam)
                .append(" OR ").append(column).append(" LIKE :").append(prefixParam)
                .append(" OR ").append(column).append(" LIKE :").append(suffixParam)
                .append(" OR ").append(column).append(" LIKE :").append(middleParam)
                .append(")");
            parameters.put(exactParam, normalized);
            parameters.put(prefixParam, normalized + ";%");
            parameters.put(suffixParam, "%;" + normalized);
            parameters.put(middleParam, "%;" + normalized + ";%");
        }
        sql.append(")");
    }

    // "status" luon la cot tinh vbden_xinykien_documents.status (khong phai dynamic_fields).
    private void appendStatusPredicate(
            StringBuilder sql, Map<String, Object> parameters, Map<String, List<String>> fieldValues, boolean include) {
        if (fieldValues == null) {
            return;
        }
        List<String> statuses = fieldValues.get(STATUS_FIELD_NAME);
        if (statuses == null || statuses.isEmpty()) {
            return;
        }
        List<String> paramRefs = new ArrayList<>();
        String prefix = include ? "incStatus" : "excStatus";
        for (int index = 0; index < statuses.size(); index++) {
            String parameterName = prefix + index;
            paramRefs.add(":" + parameterName);
            parameters.put(parameterName, statuses.get(index) == null ? "" : statuses.get(index).trim().toLowerCase());
        }
        sql.append(include ? " AND LOWER(COALESCE(d.status, '')) IN (" : " AND LOWER(COALESCE(d.status, '')) NOT IN (")
            .append(String.join(",", paramRefs)).append(")");
    }

    // Moi field khac "status" la field dong (dynamic_fields JSONB).
    private void appendDynamicFieldValuePredicates(
            StringBuilder sql, Map<String, Object> parameters, Map<String, List<String>> fieldValues, boolean include) {
        if (fieldValues == null) {
            return;
        }
        int index = 0;
        for (Map.Entry<String, List<String>> entry : fieldValues.entrySet()) {
            if (STATUS_FIELD_NAME.equals(entry.getKey())) {
                continue;
            }
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                continue;
            }
            String paramPrefix = (include ? "incDyn" : "excDyn") + index++;
            List<String> paramRefs = new ArrayList<>();
            for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                String parameterName = paramPrefix + "_" + valueIndex;
                paramRefs.add(":" + parameterName);
                parameters.put(parameterName, values.get(valueIndex) == null ? "" : values.get(valueIndex));
            }
            sql.append(" AND COALESCE(d.dynamic_fields->>'").append(entry.getKey()).append("', '')")
                .append(include ? " IN (" : " NOT IN (")
                .append(String.join(",", paramRefs)).append(")");
        }
    }

    private void appendKeywordPredicate(
            StringBuilder sql, Map<String, Object> parameters, String keyword, List<String> keywordColumns) {
        if (keyword == null || keyword.isBlank() || keywordColumns == null || keywordColumns.isEmpty()) {
            return;
        }
        String likeValue = SearchTextSql.containsLike(keyword); // 20/10/2026: không phân biệt dấu
        sql.append(" AND (");
        for (int index = 0; index < keywordColumns.size(); index++) {
            if (index > 0) {
                sql.append(" OR ");
            }
            String parameterName = "keyword" + index;
            sql.append("td_search_norm(COALESCE(d.dynamic_fields->>'").append(keywordColumns.get(index)).append("', ''))")
                .append(" LIKE :").append(parameterName);
            parameters.put(parameterName, likeValue);
        }
        sql.append(")");
    }

    private long countNative(String sql, Map<String, Object> parameters) {
        Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM (" + sql + ") AS matched");
        parameters.forEach(countQuery::setParameter);
        return ((Number) countQuery.getSingleResult()).longValue();
    }
}
