package com.td.infrastructure.persistence.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.vbcddh.VbcddhDocumentRepository;
import com.td.application.vbcddh.VbcddhListCriteria;
import com.td.domain.vbcddh.VbcddhDocument;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * BL-250 (VBCDDH Core), bổ sung 08/09/2026 (yêu cầu người dùng "tách vbcddh khỏi bảng dùng chung"):
 * adapter cho bảng vật lý ĐỘC LẬP {@code vbcddh_documents} (xem javadoc {@link VbcddhDocument}) —
 * KHÔNG dùng lại {@code DocumentRepository} (query cứng "documents"). Copy CÓ CHỦ Ý predicate SQL
 * của {@code DocumentRepository#findByFieldValue}/{@code PostgresVbdiDocumentRepository
 * #findByAnyFieldContainsMember} (chỉ đổi tên bảng/entity đích) — KHÔNG viết lại thuật toán, đúng
 * tiền lệ {@code PostgresVbdiDocumentRepository}.
 */
@Repository
public class PostgresVbcddhDocumentRepository implements VbcddhDocumentRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbcddhDocumentJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresVbcddhDocumentRepository(VbcddhDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<VbcddhDocument> findById(UUID id) {
        // Ep kieu ro JpaRepository<VbcddhDocument, UUID> - BaseRepository ke thua CA CrudRepository
        // (Spring Data) LAN IRepository (port rieng) nen goi thang qua bien jpaRepository (kieu
        // VbcddhDocumentJpaRepository) bi ambiguous - dung tien le PostgresVbdiDocumentRepository#findById.
        return ((org.springframework.data.jpa.repository.JpaRepository<VbcddhDocument, UUID>) jpaRepository).findById(id);
    }

    @Override
    public <S extends VbcddhDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public void delete(VbcddhDocument entity) {
        jpaRepository.delete(entity);
    }

    @Override
    public Page<VbcddhDocument> findByFieldValue(
            String documentType, String fieldName, String fieldValue, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbcddh_documents d
            WHERE d.deleted_on IS NULL
              AND d.dynamic_fields @> CAST(:fieldValueFilter AS jsonb)
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("fieldValueFilter", buildFieldValueFilter(fieldName, fieldValue));
        appendDocumentTypeFilter(sql, parameters, documentType);

        return executeQuery(sql, parameters, pageable);
    }

    @Override
    public Page<VbcddhDocument> findByForm(String documentType, String form, String extraPredicateSql, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbcddh_documents d
            WHERE d.deleted_on IS NULL
              AND COALESCE(d.dynamic_fields->>'form', '') = :form
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("form", form == null ? "" : form);
        appendDocumentTypeFilter(sql, parameters, documentType);
        appendExtraPredicate(sql, extraPredicateSql);

        return executeQuery(sql, parameters, pageable);
    }

    @Override
    public Page<VbcddhDocument> findByFormAndFieldValue(
            String documentType, String form, String fieldName, String fieldValue, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbcddh_documents d
            WHERE d.deleted_on IS NULL
              AND COALESCE(d.dynamic_fields->>'form', '') = :form
              AND d.dynamic_fields @> CAST(:fieldValueFilter AS jsonb)
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("form", form == null ? "" : form);
        parameters.put("fieldValueFilter", buildFieldValueFilter(fieldName, fieldValue));
        appendDocumentTypeFilter(sql, parameters, documentType);

        return executeQuery(sql, parameters, pageable);
    }

    @Override
    public Page<VbcddhDocument> findByFormAndAnyFieldContainsMember(
            String documentType, String form, List<String> fieldNames, String member,
            String extraPredicateSql, Pageable pageable) {
        if (fieldNames == null || fieldNames.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbcddh_documents d
            WHERE d.deleted_on IS NULL
              AND COALESCE(d.dynamic_fields->>'form', '') = :form
              AND (
            """);
        Map<String, Object> parameters = new LinkedHashMap<>();
        appendAnyFieldContainsMemberOr(sql, parameters, fieldNames, member);
        sql.append(")");
        parameters.put("form", form == null ? "" : form);
        appendDocumentTypeFilter(sql, parameters, documentType);
        appendExtraPredicate(sql, extraPredicateSql);

        return executeQuery(sql, parameters, pageable);
    }

    @Override
    public Page<VbcddhDocument> findByAnyFieldContainsMember(
            String documentType, List<String> fieldNames, String member, Pageable pageable) {
        if (fieldNames == null || fieldNames.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbcddh_documents d
            WHERE d.deleted_on IS NULL
              AND (
            """);
        Map<String, Object> parameters = new LinkedHashMap<>();
        appendAnyFieldContainsMemberOr(sql, parameters, fieldNames, member);
        sql.append(")");
        appendDocumentTypeFilter(sql, parameters, documentType);

        return executeQuery(sql, parameters, pageable);
    }

    @Override
    public Page<VbcddhDocument> findByFormAndAnyFieldContainsMemberAndLoaiNhiemVu(
            String documentType, String form, List<String> fieldNames, String member,
            String loaiNhiemVu, String extraPredicateSql, Pageable pageable) {
        if (fieldNames == null || fieldNames.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbcddh_documents d
            WHERE d.deleted_on IS NULL
              AND COALESCE(d.dynamic_fields->>'form', '') = :form
              AND (
            """);
        Map<String, Object> parameters = new LinkedHashMap<>();
        appendAnyFieldContainsMemberOr(sql, parameters, fieldNames, member);
        sql.append(")");
        parameters.put("form", form == null ? "" : form);
        // dxl vwNhiem_Vu_Ca_Nhan_Co_Quan cot $7: key := @If(idvanban != ""; "nvtuqlvb"; "nvtheochidao");
        // @Unique(key : "all") - "all" luon khop bat ke phan loai that.
        sql.append(" AND (:loaiNhiemVu = 'all' OR"
            + " (CASE WHEN COALESCE(d.dynamic_fields->>'idvanban', '') != '' THEN 'nvtuqlvb' ELSE 'nvtheochidao' END)"
            + " = :loaiNhiemVu)");
        parameters.put("loaiNhiemVu", loaiNhiemVu == null ? "all" : loaiNhiemVu);
        appendDocumentTypeFilter(sql, parameters, documentType);
        appendExtraPredicate(sql, extraPredicateSql);

        return executeQuery(sql, parameters, pageable);
    }

    // Tuong duong @Explode(field;";") roi so khop KHONG phan biet hoa/thuong (khac
    // PostgresVbdiDocumentRepository#findByAnyFieldContainsMember - case-SENSITIVE - vi FE vbcddh
    // (NhiemVuItemsList.jsx/ItemsList.jsx) LUON .toUpperCase() khoa truoc khi gui, xem javadoc
    // VbcddhDocumentRepository#findByFormAndAnyFieldContainsMember) - boc them ";" 2 dau field/gia
    // tri tim de tranh khop nham chuoi con.
    // 30/10/2026 (cutover FE vbcddh khoi Domino qlvb/search|view/query): loc tong quat - ten field
    // client gui (contains/dateRanges/equals) di qua THAM SO (dynamic_fields->>:kN), khong ghep chuoi.
    @Override
    public Page<VbcddhDocument> findByCriteria(VbcddhListCriteria criteria, Pageable pageable) {
        VbcddhListCriteria c = criteria == null ? VbcddhListCriteria.builder().build() : criteria;
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbcddh_documents d
            WHERE d.deleted_on IS NULL
            """);
        Map<String, Object> parameters = new LinkedHashMap<>();
        appendDocumentTypeFilter(sql, parameters, c.getDocumentType());

        if (c.getForms() != null && !c.getForms().isEmpty()) {
            sql.append(" AND COALESCE(d.dynamic_fields->>'form', '') IN (:forms)");
            parameters.put("forms", c.getForms());
        }
        if (c.getMemberFields() != null && !c.getMemberFields().isEmpty()) {
            sql.append(" AND (");
            appendAnyFieldContainsMemberOr(sql, parameters, c.getMemberFields(), c.getMember());
            sql.append(")");
        }
        int index = 0;
        if (c.getEqualsIgnoreCase() != null) {
            for (Map.Entry<String, String> entry : c.getEqualsIgnoreCase().entrySet()) {
                String k = "ek" + index;
                String v = "ev" + index;
                sql.append(" AND UPPER(TRIM(COALESCE(d.dynamic_fields->>CAST(:").append(k)
                    .append(" AS text), ''))) = UPPER(TRIM(:").append(v).append("))");
                parameters.put(k, entry.getKey());
                parameters.put(v, entry.getValue() == null ? "" : entry.getValue());
                index++;
            }
        }
        if (c.getContains() != null) {
            for (Map.Entry<String, String> entry : c.getContains().entrySet()) {
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }
                String k = "ck" + index;
                String v = "cv" + index;
                sql.append(" AND ").append(SearchTextSql.norm("COALESCE(d.dynamic_fields->>CAST(:" + k + " AS text), '')"))
                    .append(" LIKE :").append(v);
                parameters.put(k, entry.getKey());
                parameters.put(v, SearchTextSql.containsLike(entry.getValue()));
                index++;
            }
        }
        if (c.getDateRanges() != null) {
            for (Map.Entry<String, String[]> entry : c.getDateRanges().entrySet()) {
                String[] range = entry.getValue();
                if (range == null || range.length < 2 || isBlank(range[0]) || isBlank(range[1])) {
                    continue;
                }
                String k = "dk" + index;
                String expr = "SUBSTRING(COALESCE(d.dynamic_fields->>CAST(:" + k + " AS text), '') FROM 1 FOR 10)";
                sql.append(" AND ").append(expr).append(" ~ '^[0-9]{2}/[0-9]{2}/[0-9]{4}$'")
                    .append(" AND TO_DATE(").append(expr).append(", 'DD/MM/YYYY') BETWEEN TO_DATE(:df").append(index)
                    .append(", 'DD/MM/YYYY') AND TO_DATE(:dt").append(index).append(", 'DD/MM/YYYY')");
                parameters.put(k, entry.getKey());
                parameters.put("df" + index, range[0].trim());
                parameters.put("dt" + index, range[1].trim());
                index++;
            }
        }
        if (!isBlank(c.getKeyword())) {
            sql.append(" AND (").append(SearchTextSql.norm("COALESCE(d.title, '')")).append(" LIKE :kw");
            List<String> keywordFields = c.getKeywordFields() == null ? List.of() : c.getKeywordFields();
            for (int i = 0; i < keywordFields.size(); i++) {
                String k = "kwf" + i;
                sql.append(" OR ").append(SearchTextSql.norm("COALESCE(d.dynamic_fields->>CAST(:" + k + " AS text), '')"))
                    .append(" LIKE :kw");
                parameters.put(k, keywordFields.get(i));
            }
            sql.append(")");
            parameters.put("kw", SearchTextSql.containsLike(c.getKeyword()));
        }
        appendExtraPredicate(sql, c.getExtraPredicateSql());

        return executeQuery(sql, parameters, pageable);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void appendAnyFieldContainsMemberOr(
            StringBuilder sql, Map<String, Object> parameters, List<String> fieldNames, String member) {
        for (int index = 0; index < fieldNames.size(); index++) {
            if (index > 0) {
                sql.append(" OR ");
            }
            sql.append("(';' || UPPER(TRIM(COALESCE(d.dynamic_fields->>'").append(fieldNames.get(index))
                .append("', ''))) || ';') LIKE '%;' || UPPER(TRIM(:member)) || ';%'");
        }
        parameters.put("member", member == null ? "" : member);
    }

    private void appendDocumentTypeFilter(StringBuilder sql, Map<String, Object> parameters, String documentType) {
        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.document_type, '')) = :documentType");
            parameters.put("documentType", documentType.trim().toLowerCase());
        }
    }

    // extraPredicateSql: hang so noi bo CO DINH duoc dinh nghia trong ViewQueryService (khong bao gio
    // tu client) - xem javadoc VbcddhDocumentRepository#findByFormAndAnyFieldContainsMember, dung
    // tien le VbdiDocumentOutFilter#extraPredicateSql.
    private void appendExtraPredicate(StringBuilder sql, String extraPredicateSql) {
        if (extraPredicateSql != null && !extraPredicateSql.isBlank()) {
            sql.append(" AND (").append(extraPredicateSql).append(")");
        }
    }

    private Page<VbcddhDocument> executeQuery(StringBuilder sql, Map<String, Object> parameters, Pageable pageable) {
        long total = countNative(sql.toString(), parameters);

        // 30/10/2026 (cutover FE vbcddh): mới nhất trước - FE luôn gửi orderBy createdOn DESC (kể cả
        // các truy vấn "lấy bản tiến độ/kết quả cuối" pageSize=1).
        sql.append(" ORDER BY d.created_on DESC NULLS LAST LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), VbcddhDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<VbcddhDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    private String buildFieldValueFilter(String fieldName, String fieldValue) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(fieldName, fieldValue == null ? "" : fieldValue));
        } catch (Exception ex) {
            return "{}";
        }
    }

    private long countNative(String sql, Map<String, Object> parameters) {
        Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM (" + sql + ") AS matched");
        parameters.forEach(countQuery::setParameter);
        return ((Number) countQuery.getSingleResult()).longValue();
    }
}
