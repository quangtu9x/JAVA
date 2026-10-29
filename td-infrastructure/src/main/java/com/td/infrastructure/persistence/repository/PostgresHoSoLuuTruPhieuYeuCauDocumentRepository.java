package com.td.infrastructure.persistence.repository;

import com.td.application.hosoluutru.HoSoLuuTruPhieuYeuCauDocumentRepository;
import com.td.domain.hosoluutru.HoSoLuuTruPhieuYeuCauDocument;
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
 * Port module "Hồ sơ lưu trữ" — adapter cho bảng vật lý ĐỘC LẬP
 * {@code hosoluutru_phieu_yeu_cau_documents}. Predicate SQL đúng khuôn
 * {@code PostgresHoSoLuuTruDocumentRepository}.
 */
@Repository
public class PostgresHoSoLuuTruPhieuYeuCauDocumentRepository implements HoSoLuuTruPhieuYeuCauDocumentRepository {

    private static final int MAX_ROWS = 2000;

    private final HoSoLuuTruPhieuYeuCauDocumentJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresHoSoLuuTruPhieuYeuCauDocumentRepository(HoSoLuuTruPhieuYeuCauDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<HoSoLuuTruPhieuYeuCauDocument> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<HoSoLuuTruPhieuYeuCauDocument, UUID>)
                jpaRepository).findById(id);
    }

    @Override
    public <S extends HoSoLuuTruPhieuYeuCauDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public void delete(HoSoLuuTruPhieuYeuCauDocument entity) {
        jpaRepository.delete(entity);
    }

    @Override
    public List<HoSoLuuTruPhieuYeuCauDocument> findByHosoId(UUID hosoId) {
        String sql = """
            SELECT d.*
            FROM hosoluutru_phieu_yeu_cau_documents d
            WHERE d.deleted_on IS NULL
              AND COALESCE(d.dynamic_fields->>'hoso_id', '') = :hosoId
            ORDER BY d.created_on ASC
            LIMIT :limit
            """;
        Query query = entityManager.createNativeQuery(sql, HoSoLuuTruPhieuYeuCauDocument.class);
        query.setParameter("hosoId", hosoId.toString());
        query.setParameter("limit", MAX_ROWS);

        @SuppressWarnings("unchecked")
        List<HoSoLuuTruPhieuYeuCauDocument> result = query.getResultList();
        return result;
    }

    @Override
    public Page<HoSoLuuTruPhieuYeuCauDocument> findByCriteria(
            List<String> matchAnyFieldNames, String matchValue,
            Map<String, List<String>> fieldValueIn,
            Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM hosoluutru_phieu_yeu_cau_documents d
            WHERE d.deleted_on IS NULL
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        if (matchAnyFieldNames != null && !matchAnyFieldNames.isEmpty()) {
            appendAnyFieldContainsPredicate(sql, parameters, matchAnyFieldNames, matchValue);
        }
        appendFieldValueInPredicate(sql, parameters, fieldValueIn);

        long total = countNative(sql.toString(), parameters);

        // Bugfix 09/09/2026 (báo lỗi người dùng: UnsupportedOperationException tại Unpaged.getPageSize
        // - lộ ra khi GetSoLuongCanXuLyUseCase lần đầu gọi tới queryPhieuYeuCauQuaHan, nơi TRUYỀN
        // Pageable.unpaged() có chủ đích để lọc quá hạn trên TOÀN BỘ kết quả trước khi tự phân trang
        // lại - Pageable.unpaged() KHÔNG hỗ trợ getPageSize()/getOffset() (ném exception theo thiết
        // kế Spring Data), phải tự kiểm tra pageable.isPaged() trước khi gọi, giống MAX_ROWS đã dùng
        // ở findByHosoId cho cùng nhu cầu "lấy hết trong giới hạn an toàn").
        sql.append(" ORDER BY d.created_on DESC");
        if (pageable.isPaged()) {
            sql.append(" LIMIT :limit OFFSET :offset");
        } else {
            sql.append(" LIMIT :limit");
        }
        Query query = entityManager.createNativeQuery(sql.toString(), HoSoLuuTruPhieuYeuCauDocument.class);
        parameters.forEach(query::setParameter);
        if (pageable.isPaged()) {
            query.setParameter("limit", pageable.getPageSize());
            query.setParameter("offset", pageable.getOffset());
        } else {
            query.setParameter("limit", MAX_ROWS);
        }

        @SuppressWarnings("unchecked")
        List<HoSoLuuTruPhieuYeuCauDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
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
            String scalarParam = "matchField" + index;
            sql.append("(COALESCE(d.dynamic_fields->>'").append(fieldNames.get(index)).append("', '') = :")
                .append(scalarParam);
            // 27/09/2026: + khop thanh vien chuoi "a;b" - xem javadoc DelimitedMemberSql.
            DelimitedMemberSql.appendOrMember(sql, parameters, fieldNames.get(index), value, "matchFieldMember" + index);
            sql.append(")");
            parameters.put(scalarParam, value == null ? "" : value);
        }
        sql.append(")");
    }

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
            String column = "LOWER(COALESCE(d.dynamic_fields->>'" + KeywordSql.requireSafeFieldName(entry.getKey()) + "', ''))";
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
