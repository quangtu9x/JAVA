package com.td.infrastructure.persistence.repository;

import com.td.application.duthao.DraftProcessDocumentRepository;
import com.td.domain.duthao.DraftProcessDocument;
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

/**
 * DT-007 (tách draft_process sang bảng riêng, yêu cầu người dùng 25/08/2026): adapter cho bảng vật
 * lý ĐỘC LẬP {@code draft_process_documents} (xem {@link DraftProcessDocument}) — KHÔNG dùng lại
 * {@code PostgresDuThaoDocumentRepository} (query cứng {@code duthao_documents}) hay
 * {@code DocumentRepository} (bảng {@code documents} dùng chung).
 *
 * <p>{@code save} — bản ghi quá trình xử lý là APPEND-ONLY (mỗi action workflow ghi 1 vết, không
 * sửa/xoá). {@code findByParentId} (26/08/2026, port {@code vwGroup_process_by_draft_id}) — SQL
 * cùng khuôn {@code DocumentRepository#findByParentId} (containment/equality theo field động
 * {@code parentid}), viết trực tiếp thay vì kế thừa vì {@code DraftProcessDocument} không cùng
 * kiểu {@code BusinessDocument}.</p>
 */
@Repository
public class PostgresDraftProcessDocumentRepository implements DraftProcessDocumentRepository {

    private final DraftProcessDocumentJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresDraftProcessDocumentRepository(DraftProcessDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public <S extends DraftProcessDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public Page<DraftProcessDocument> findByParentId(String parentId, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*
            FROM draft_process_documents d
            WHERE d.deleted_on IS NULL
            AND COALESCE(d.dynamic_fields->>'parentid', '') = :parentId
            """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("parentId", parentId == null ? "" : parentId);

        long total = countNative(sql.toString(), parameters);

        // Moi nhat truoc - dung orderBy Domino goc FE dang gui (khac ASC mac dinh cua
        // DocumentRepository#findByParentId), xem javadoc port interface.
        sql.append(" ORDER BY d.created_on DESC NULLS LAST LIMIT :limit OFFSET :offset");
        Query query = entityManager.createNativeQuery(sql.toString(), DraftProcessDocument.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<DraftProcessDocument> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    private long countNative(String sql, Map<String, Object> parameters) {
        Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM (" + sql + ") AS matched");
        parameters.forEach(countQuery::setParameter);
        return ((Number) countQuery.getSingleResult()).longValue();
    }
}