package com.td.infrastructure.persistence.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.documents.VbdenOrgViewFilter;
import com.td.application.hosocv.HoSoCVDocumentRepository;
import com.td.domain.hosocv.HoSoCVDocument;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Mở rộng BL-240 (HoSoCV Core), yêu cầu người dùng ("tạo bảng mới, sử dụng jsonb (dynamic field)
 * như các module đã thực hiện"): adapter cho bảng vật lý ĐỘC LẬP {@code hosocv_documents} (xem
 * javadoc {@link HoSoCVDocument}) — KHÔNG dùng lại {@code PostgresDocumentRepository}. Copy CÓ CHỦ
 * Ý phần predicate SQL (containment/status IN/keyword) từ {@code PostgresDuThaoDocumentRepository}
 * (đã qua nhiều bugfix thực tế trong phiên vbden/vbdi/duthao) — chỉ đổi tên bảng/entity, không viết
 * lại thuật toán.
 */
@Repository
public class PostgresHoSoCVDocumentRepository implements HoSoCVDocumentRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String STATUS_FIELD_NAME = "status";
    private static final String DD_MM_YYYY_REGEX = "^[0-9]{2}/[0-9]{2}/[0-9]{4}$";

    private final HoSoCVDocumentJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresHoSoCVDocumentRepository(HoSoCVDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<HoSoCVDocument> findById(UUID id) {
        // Ep kieu ro JpaRepository<HoSoCVDocument, UUID> - dung tien le PostgresDuThaoDocumentRepository#findById
        // (BaseRepository ke thua CA CrudRepository LAN IRepository, goi thang qua bien jpaRepository se ambiguous).
        return ((org.springframework.data.jpa.repository.JpaRepository<HoSoCVDocument, UUID>) jpaRepository).findById(id);
    }

    @Override
    public <S extends HoSoCVDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public void delete(HoSoCVDocument entity) {
        jpaRepository.delete(entity);
    }

    @Override
    public Page<HoSoCVDocument> findByAnyFieldContains(
            String documentType, List<String> fieldNames, String value, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM hosocv_documents d
            WHERE d.deleted_on IS NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        appendAnyFieldContainsPredicate(sql, parameters, fieldNames, value);
        appendDocumentTypeFilter(sql, parameters, documentType);

        return executeQuery(sql, parameters, pageable, "d.created_on ASC NULLS LAST");
    }

    @Override
    public Page<HoSoCVDocument> findByVbdenOrgView(
            String documentType, VbdenOrgViewFilter filter, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM hosocv_documents d
            WHERE d.deleted_on IS NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
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
        appendStatusPredicate(sql, parameters, filter.getIncludeFieldValues(), true);
        appendStatusPredicate(sql, parameters, filter.getExcludeFieldValues(), false);
        appendDynamicFieldValuePredicates(sql, parameters, filter.getIncludeFieldValues(), true);
        appendDynamicFieldValuePredicates(sql, parameters, filter.getExcludeFieldValues(), false);
        // 09/09/2026 ("tim kiem nang cao" ho so - cutover qlvb/search?databasetype=hosocv): field ->
        // danh sach gia tri, khop IT NHAT 1 (OR) - dung cho field text tu do cua CollapseHoSoCV.jsx
        // (name/title/first_topic/second_topic) - xem javadoc buildHoSoCVAdvancedSearchFilter.
        appendContainsAnyFieldValuePredicates(sql, parameters, filter.getContainsAnyFieldValues());
        appendExcludeMemberFieldValuePredicates(sql, parameters, filter.getExcludeMemberFieldValues());
        if (filter.getRequireEmptyFieldName() != null && !filter.getRequireEmptyFieldName().isBlank()) {
            sql.append(" AND COALESCE(d.dynamic_fields->>'")
                .append(filter.getRequireEmptyFieldName()).append("', '') = ''");
        }
        if (filter.getRequireNonEmptyFieldName() != null && !filter.getRequireNonEmptyFieldName().isBlank()) {
            sql.append(" AND COALESCE(d.dynamic_fields->>'")
                .append(filter.getRequireNonEmptyFieldName()).append("', '') <> ''");
        }
        appendKeywordPredicate(sql, parameters, filter.getKeyword(), filter.getKeywordColumns());
        // 09/09/2026 ("tim kiem nang cao" ho so): "Ngay tao" (CollapseHoSoCV.jsx#create_date) khop
        // dung ngu nghia cot tinh created_on (AuditableEntity) - tai dung minCreatedOn/maxCreatedOn
        // (da co san tu Slice 9, chua duoc wire o day truoc gio vi hosocv chua co view nao can no).
        if (filter.getMinCreatedOn() != null) {
            sql.append(" AND CAST(d.created_on AS date) >= :minCreatedOn");
            parameters.put("minCreatedOn", java.sql.Date.valueOf(filter.getMinCreatedOn()));
        }
        if (filter.getMaxCreatedOn() != null) {
            sql.append(" AND CAST(d.created_on AS date) <= :maxCreatedOn");
            parameters.put("maxCreatedOn", java.sql.Date.valueOf(filter.getMaxCreatedOn()));
        }
        appendDateFieldInRangePredicate(sql, parameters, "end_date",
            "minEndDate", filter.getMinEndDate(), "maxEndDate", filter.getMaxEndDate());
        appendDateFieldInRangePredicate(sql, parameters, "storaged_date",
            "minStoragedDate", filter.getMinStoragedDate(), "maxStoragedDate", filter.getMaxStoragedDate());
        appendDocumentTypeFilter(sql, parameters, documentType);

        // 02/10/2026: "van ban khac" cua ho so (vwOther_Related_Document_Document_Profiles_ID) sap
        // @Created giam dan nhu Domino - xem VbdenOrgViewFilter#newestCreatedFirst.
        return executeQuery(sql, parameters, pageable, filter.isNewestCreatedFirst()
            ? "d.created_on DESC NULLS LAST"
            : "d.last_modified_on DESC NULLS LAST");
    }

    @Override
    public Page<HoSoCVDocument> findByListFieldMemberExcludingOther(
            String documentType, String includeListField, String excludeListField, String value,
            Pageable pageable) {
        return findByListFieldMemberExcludingOther(documentType, includeListField, excludeListField, value, null, null, pageable);
    }

    // 01/10/2026: + keyword/keywordColumns ("tim trong danh sach").
    @Override
    public Page<HoSoCVDocument> findByListFieldMemberExcludingOther(
            String documentType, String includeListField, String excludeListField, String value,
            String keyword, List<String> keywordColumns, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM hosocv_documents d
            WHERE d.deleted_on IS NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        appendAnyListFieldMemberPredicate(sql, parameters, List.of(includeListField), value);
        if (excludeListField != null && !excludeListField.isBlank()) {
            appendAnyListFieldMemberPredicate(sql, parameters, List.of(excludeListField), value, true);
        }
        appendKeywordPredicate(sql, parameters, keyword, keywordColumns);
        appendDocumentTypeFilter(sql, parameters, documentType);

        return executeQuery(sql, parameters, pageable, "d.last_modified_on DESC NULLS LAST");
    }

    @Override
    public Page<HoSoCVDocument> findDeleted(String documentType, Pageable pageable) {
        return findDeleted(documentType, null, null, pageable);
    }

    // 01/10/2026: + keyword/keywordColumns ("tim trong danh sach").
    @Override
    public Page<HoSoCVDocument> findDeleted(String documentType, String keyword, List<String> keywordColumns, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM hosocv_documents d
            WHERE d.deleted_on IS NOT NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        appendKeywordPredicate(sql, parameters, keyword, keywordColumns);
        appendDocumentTypeFilter(sql, parameters, documentType);

        return executeQuery(sql, parameters, pageable, "d.deleted_on DESC NULLS LAST");
    }

    @Override
    public Page<HoSoCVDocument> findByFieldValueDueDate(
            String documentType, String keyFieldName, String keyValue,
            String dateFieldName, boolean strictlyBefore,
            Map<String, String> extraDynamicFieldEquals, Pageable pageable) {
        return findByFieldValueDueDate(documentType, keyFieldName, keyValue, dateFieldName, strictlyBefore,
            extraDynamicFieldEquals, null, null, pageable);
    }

    // 01/10/2026: + keyword/keywordColumns ("tim trong danh sach").
    @Override
    public Page<HoSoCVDocument> findByFieldValueDueDate(
            String documentType, String keyFieldName, String keyValue,
            String dateFieldName, boolean strictlyBefore,
            Map<String, String> extraDynamicFieldEquals, String keyword, List<String> keywordColumns,
            Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM hosocv_documents d
            WHERE d.deleted_on IS NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        if (keyFieldName != null && !keyFieldName.isBlank() && keyValue != null && !keyValue.isBlank()) {
            appendAnyFieldContainsPredicate(sql, parameters, List.of(keyFieldName), keyValue);
        }
        // Cung cong thuc TO_DATE(...,'DD/MM/YYYY') + guard regex da dung cho deadline_date/received_date
        // cua vbden (xem PostgresDocumentRepository#appendDeadlineInRangePredicate) - date luu dang
        // chuoi "dd/MM/yyyy", KHONG PHAI ISO.
        String formatParam = "dateFormat_" + dateFieldName.hashCode();
        sql.append(" AND (d.dynamic_fields->>'").append(dateFieldName).append("') ~ :").append(formatParam);
        parameters.put(formatParam, DD_MM_YYYY_REGEX);
        sql.append(" AND TO_DATE(d.dynamic_fields->>'").append(dateFieldName).append("', 'DD/MM/YYYY') ")
            .append(strictlyBefore ? "< " : "<= ").append(":dueDateThreshold");
        parameters.put("dueDateThreshold", java.sql.Date.valueOf(java.time.LocalDate.now()));

        if (extraDynamicFieldEquals != null) {
            int index = 0;
            for (Map.Entry<String, String> entry : extraDynamicFieldEquals.entrySet()) {
                String paramName = "extraEquals" + (index++);
                // "status" la cot tinh cua hosocv_documents (khong phai field dong JSONB) - dung
                // cong thuc rieng, giong appendStatusPredicate/STATUS_FIELD_NAME o cac method khac
                // trong chinh file nay.
                if (STATUS_FIELD_NAME.equals(entry.getKey())) {
                    sql.append(" AND LOWER(COALESCE(d.status, '')) = :").append(paramName);
                    parameters.put(paramName, entry.getValue() == null ? "" : entry.getValue().trim().toLowerCase());
                } else {
                    sql.append(" AND COALESCE(d.dynamic_fields->>'").append(entry.getKey()).append("', '') = :")
                        .append(paramName);
                    parameters.put(paramName, entry.getValue() == null ? "" : entry.getValue());
                }
            }
        }
        appendKeywordPredicate(sql, parameters, keyword, keywordColumns);
        appendDocumentTypeFilter(sql, parameters, documentType);

        return executeQuery(sql, parameters, pageable, "d.last_modified_on DESC NULLS LAST");
    }

    // ---- helpers (copy tu PostgresDuThaoDocumentRepository, chi doi ten bang) ----

    private Page<HoSoCVDocument> executeQuery(
            StringBuilder sql, Map<String, Object> parameters, Pageable pageable, String orderBy) {
        long total = countNative(sql.toString(), parameters);

        sql.append(" ORDER BY ").append(orderBy).append(" LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), HoSoCVDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<HoSoCVDocument> content = query.getResultList();
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

    // Package-private (khong private) de unit-test truc tiep SQL builder khong can EntityManager -
    // cung khuon appendAnyFieldContainsPredicate.
    void appendAnyListFieldMemberPredicate(
            StringBuilder sql, Map<String, Object> parameters, List<String> fieldNames, String value) {
        appendAnyListFieldMemberPredicate(sql, parameters, fieldNames, value, false);
    }

    // negate=true -> KHONG la thanh vien cua BAT KY field nao (dung cho "_chua": loai tru user_coordinated).
    void appendAnyListFieldMemberPredicate(
            StringBuilder sql, Map<String, Object> parameters, List<String> fieldNames, String value, boolean negate) {
        if (fieldNames == null || fieldNames.isEmpty()) {
            sql.append(negate ? "" : " AND 1 = 0");
            return;
        }
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (normalized.isEmpty()) {
            sql.append(negate ? "" : " AND 1 = 0");
            return;
        }
        sql.append(negate ? " AND NOT (" : " AND (");
        for (int index = 0; index < fieldNames.size(); index++) {
            if (index > 0) {
                sql.append(" OR ");
            }
            String fieldName = fieldNames.get(index);
            String prefix = (negate ? "excl" : "incl") + index;
            String column = "LOWER(COALESCE(d.dynamic_fields->>'" + fieldName + "', ''))";
            String exactParam = prefix + "Exact";
            String prefixParam = prefix + "Prefix";
            String suffixParam = prefix + "Suffix";
            String middleParam = prefix + "Middle";
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

    // 09/09/2026 ("tim kiem nang cao"): field -> danh sach gia tri, field PHAI khop IT NHAT 1 gia tri
    // (OR) - moi gia tri thu ca 2 dang: mang JSON chua phan tu ("@>" + jsonb_build_array) HOAC scalar
    // bang chuoi - cung ky thuat da dung cho vbdi (PostgresVbdiDocumentRepository), viet lai cho
    // hosocv_documents.
    private void appendContainsAnyFieldValuePredicates(
            StringBuilder sql, Map<String, Object> parameters, Map<String, List<String>> containsAnyFieldValues) {
        if (containsAnyFieldValues == null || containsAnyFieldValues.isEmpty()) {
            return;
        }
        int fieldIndex = 0;
        for (Map.Entry<String, List<String>> entry : containsAnyFieldValues.entrySet()) {
            String fieldName = entry.getKey();
            List<String> values = entry.getValue();
            if (fieldName == null || fieldName.isBlank() || values == null || values.isEmpty()) {
                fieldIndex++;
                continue;
            }
            sql.append(" AND (");
            boolean first = true;
            for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                String value = values.get(valueIndex);
                if (value == null || value.isBlank()) {
                    continue;
                }
                if (!first) {
                    sql.append(" OR ");
                }
                first = false;
                String containsParam = "anyVal" + fieldIndex + "_" + valueIndex;
                String scalarParam = "anyValScalar" + fieldIndex + "_" + valueIndex;
                sql.append("(d.dynamic_fields->'").append(fieldName).append("' @> jsonb_build_array(CAST(:")
                    .append(containsParam).append(" AS text))")
                    .append(" OR COALESCE(d.dynamic_fields->>'").append(fieldName).append("', '') = :")
                    .append(scalarParam);
                // 01/10/2026: + thanh vien chuoi "a;b" (vd coordinator/user_coordinated nhieu nguoi).
                DelimitedMemberSql.appendOrMember(sql, parameters, fieldName, value,
                    "anyValMember" + fieldIndex + "_" + valueIndex);
                sql.append(")");
                parameters.put(containsParam, value);
                parameters.put(scalarParam, value);
            }
            if (first) {
                sql.append("1 = 0");
            }
            sql.append(")");
            fieldIndex++;
        }
    }

    // 01/10/2026 - Domino "fieldnot_X" (!@Contains): field KHONG chua gia tri o ca 3 dang (phan tu mang
    // JSON, scalar bang, thanh vien chuoi "a;b") - xem javadoc VbdenOrgViewFilter#excludeMemberFieldValues.
    // Package-private cho unit test SQL builder.
    void appendExcludeMemberFieldValuePredicates(
            StringBuilder sql, Map<String, Object> parameters, Map<String, List<String>> excludeMemberFieldValues) {
        if (excludeMemberFieldValues == null || excludeMemberFieldValues.isEmpty()) {
            return;
        }
        int fieldIndex = 0;
        for (Map.Entry<String, List<String>> entry : excludeMemberFieldValues.entrySet()) {
            String fieldName = entry.getKey();
            List<String> values = entry.getValue();
            if (fieldName == null || fieldName.isBlank() || values == null) {
                fieldIndex++;
                continue;
            }
            for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                String value = values.get(valueIndex);
                if (value == null || value.isBlank()) {
                    continue;
                }
                String containsParam = "noneVal" + fieldIndex + "_" + valueIndex;
                String scalarParam = "noneValScalar" + fieldIndex + "_" + valueIndex;
                sql.append(" AND NOT (COALESCE(d.dynamic_fields->'").append(fieldName)
                    .append("' @> jsonb_build_array(CAST(:").append(containsParam).append(" AS text)), false)")
                    .append(" OR COALESCE(d.dynamic_fields->>'").append(fieldName).append("', '') = :")
                    .append(scalarParam);
                DelimitedMemberSql.appendOrMember(sql, parameters, fieldName, value,
                    "noneValMember" + fieldIndex + "_" + valueIndex);
                sql.append(")");
                parameters.put(containsParam, value);
                parameters.put(scalarParam, value);
            }
            fieldIndex++;
        }
    }

    // 09/09/2026 ("tim kiem nang cao" ho so - end_date/storaged_date): field dong luon luu chuoi
    // "dd/MM/yyyy" (cung quy uoc deadline_date/received_date cua vbden, xem appendReceivedDateInRangePredicate
    // ben PostgresDocumentRepository) - guard regex truoc TO_DATE de khong throw voi du lieu cu/sai dang.
    private void appendDateFieldInRangePredicate(
            StringBuilder sql, Map<String, Object> parameters, String fieldName,
            String minParamName, LocalDate minValue, String maxParamName, LocalDate maxValue) {
        if (minValue == null && maxValue == null) {
            return;
        }
        String formatParam = minParamName + "Format";
        sql.append(" AND (d.dynamic_fields->>'").append(fieldName).append("') ~ :").append(formatParam);
        parameters.put(formatParam, DD_MM_YYYY_REGEX);
        if (minValue != null) {
            sql.append(" AND TO_DATE(d.dynamic_fields->>'").append(fieldName)
                .append("', 'DD/MM/YYYY') >= :").append(minParamName);
            parameters.put(minParamName, java.sql.Date.valueOf(minValue));
        }
        if (maxValue != null) {
            sql.append(" AND TO_DATE(d.dynamic_fields->>'").append(fieldName)
                .append("', 'DD/MM/YYYY') <= :").append(maxParamName);
            parameters.put(maxParamName, java.sql.Date.valueOf(maxValue));
        }
    }

    private void appendKeywordPredicate(
            StringBuilder sql, Map<String, Object> parameters, String keyword, List<String> keywordColumns) {
        // 01/10/2026: xem KeywordSql (title + chong SQL injection ten cot).
        KeywordSql.append(sql, parameters, keyword, keywordColumns, "d");
    }

    private long countNative(String sql, Map<String, Object> parameters) {
        Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM (" + sql + ") AS matched");
        parameters.forEach(countQuery::setParameter);
        return ((Number) countQuery.getSingleResult()).longValue();
    }
}
