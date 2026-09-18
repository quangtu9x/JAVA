package com.td.infrastructure.persistence.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.vbden.VbdenResDocumentRepository;
import com.td.domain.vbden.VbdenResDocument;
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
 * BL-210 (VBDen Core) — DoD (4), yêu cầu người dùng 05/09/2026 ("tách vbden_res sang bảng riêng"):
 * adapter cho bảng vật lý ĐỘC LẬP {@code vbden_res_documents} (xem {@link VbdenResDocument}) —
 * KHÔNG dùng lại {@code PostgresDocumentRepository} (query cứng {@code documents}, bảng dùng chung).
 *
 * <p>{@code save} — bản ghi quá trình xử lý là APPEND-ONLY (mỗi hành động ghi 1 vết, không sửa/xoá).
 * {@code findByParentId}/{@code findByFieldValue} — SQL cùng khuôn
 * {@code DocumentRepository#findByParentId}/{@code #findByFieldValue} (containment JSONB theo field
 * động), viết trực tiếp thay vì kế thừa vì {@link VbdenResDocument} không cùng kiểu
 * {@code BusinessDocument}. GIỮ NGUYÊN {@code ORDER BY created_on ASC} của bản gốc (khác
 * {@code PostgresDraftProcessDocumentRepository}, vốn đổi sang DESC theo yêu cầu riêng của cụm dự
 * thảo) — không đổi thứ tự hiển thị đã ship của {@code QuaTrinhXuLy}/{@code LichSuXuLy}/
 * {@code ThuHoiVanBan}.</p>
 */
@Repository
public class PostgresVbdenResDocumentRepository implements VbdenResDocumentRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PARENT_ID_FIELD = "parentid";

    private final VbdenResDocumentJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresVbdenResDocumentRepository(VbdenResDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public <S extends VbdenResDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public Optional<VbdenResDocument> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Page<VbdenResDocument> findByParentId(String parentId, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbden_res_documents d
            WHERE d.deleted_on IS NULL
            AND d.dynamic_fields @> CAST(:parentIdFilter AS jsonb)
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("parentIdFilter", buildFieldFilter(PARENT_ID_FIELD, parentId));

        long total = countNative(sql.toString(), parameters);

        // Giu nguyen ASC (cu->moi) dung DocumentRepository#findByParentId goc, xem javadoc lop.
        sql.append(" ORDER BY d.created_on ASC NULLS LAST LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), VbdenResDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<VbdenResDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<VbdenResDocument> findByFieldValue(String fieldName, String fieldValue, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM vbden_res_documents d
            WHERE d.deleted_on IS NULL
            AND d.dynamic_fields @> CAST(:fieldValueFilter AS jsonb)
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("fieldValueFilter", buildFieldFilter(fieldName, fieldValue));

        long total = countNative(sql.toString(), parameters);

        sql.append(" ORDER BY d.created_on ASC NULLS LAST LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), VbdenResDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<VbdenResDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    String buildFieldFilter(String fieldName, String fieldValue) {
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
