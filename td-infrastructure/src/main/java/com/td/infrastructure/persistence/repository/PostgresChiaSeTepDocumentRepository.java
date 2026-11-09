package com.td.infrastructure.persistence.repository;

import com.td.application.chiasetep.ChiaSeTepDocumentRepository;
import com.td.domain.chiasetep.ChiaSeTepDocument;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 30/10/2026 (port chiasetep): adapter {@code chiasetep_documents} - SQL native, chỉ bản ghi chưa xoá.
 * Danh sách người chia sẻ lưu dạng "u1;u2" (Domino nguoichinhsuahide/nguoixemhide) nên so khớp phần tử sau
 * khi tách ";" + hạ thường.
 */
@Repository
public class PostgresChiaSeTepDocumentRepository implements ChiaSeTepDocumentRepository {

    private static final String FOLDER = "d.form = 'thumuc' AND d.deleted_on IS NULL";
    private static final String DOCUMENT = "d.form = 'tailieu' AND d.deleted_on IS NULL";
    private static final String KEYWORD_FIELDS = "COALESCE(d.dynamic_fields->>'sokyhieu', '') || ' ' || d.title"
        + " || ' ' || COALESCE(d.dynamic_fields->>'trichyeu', '') || ' ' || COALESCE(d.dynamic_fields->>'coquanbanhanh', '')"
        + " || ' ' || COALESCE(d.dynamic_fields->>'ghichu', '')";

    private final ChiaSeTepDocumentJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresChiaSeTepDocumentRepository(ChiaSeTepDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<ChiaSeTepDocument> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<ChiaSeTepDocument, UUID>) jpaRepository)
            .findById(id);
    }

    @Override
    public <S extends ChiaSeTepDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public void delete(ChiaSeTepDocument entity) {
        jpaRepository.delete(entity);
    }

    @Override
    public List<ChiaSeTepDocument> findFoldersOwnedBy(String owner) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("owner", lower(owner));
        return list("SELECT d.* FROM chiasetep_documents d WHERE " + FOLDER
            + " AND LOWER(COALESCE(d.user_creator, '')) = :owner", params);
    }

    @Override
    public List<ChiaSeTepDocument> findFoldersSharedBy(String owner, String recipient) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("owner", lower(owner));
        StringBuilder sql = new StringBuilder("SELECT d.* FROM chiasetep_documents d WHERE ").append(FOLDER)
            .append(" AND LOWER(COALESCE(d.user_creator, '')) = :owner");
        if (recipient == null || recipient.isBlank()) {
            sql.append(" AND (").append(notBlank("nguoichinhsuahide")).append(" OR ")
                .append(notBlank("nguoixemhide")).append(")");
        } else {
            params.put("member", lower(recipient));
            sql.append(" AND (").append(memberOf("nguoichinhsuahide")).append(" OR ")
                .append(memberOf("nguoixemhide")).append(")");
        }
        return list(sql.toString(), params);
    }

    @Override
    public List<ChiaSeTepDocument> findFoldersSharedWith(String recipient, String owner) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("member", lower(recipient));
        StringBuilder sql = new StringBuilder("SELECT d.* FROM chiasetep_documents d WHERE ").append(FOLDER)
            .append(" AND (").append(memberOf("nguoichinhsuahide")).append(" OR ")
            .append(memberOf("nguoixemhide")).append(")");
        if (owner != null && !owner.isBlank()) {
            params.put("owner", lower(owner));
            sql.append(" AND LOWER(COALESCE(d.user_creator, '')) = :owner");
        }
        return list(sql.toString(), params);
    }

    @Override
    public List<ChiaSeTepDocument> findChildren(Collection<String> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return List.of();
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("parentIds", new ArrayList<>(parentIds));
        return list("SELECT d.* FROM chiasetep_documents d WHERE d.deleted_on IS NULL AND d.parent_id IN (:parentIds)",
            params);
    }

    @Override
    public Page<ChiaSeTepDocument> findDocumentsInFolder(String parentId, String keyword, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("parentId", parentId == null ? "" : parentId);
        StringBuilder sql = new StringBuilder("SELECT d.* FROM chiasetep_documents d WHERE ").append(DOCUMENT)
            .append(" AND d.parent_id = :parentId");
        appendKeyword(sql, params, keyword);
        return page(sql.toString(), params, pageable);
    }

    @Override
    public Page<ChiaSeTepDocument> findDocumentsAccessibleTo(String username, String keyword, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("member", lower(username));
        StringBuilder sql = new StringBuilder("SELECT d.* FROM chiasetep_documents d WHERE ").append(DOCUMENT)
            .append(" AND (LOWER(COALESCE(d.user_creator, '')) = :member OR EXISTS (SELECT 1 FROM chiasetep_documents f")
            .append(" WHERE f.form = 'thumuc' AND f.deleted_on IS NULL AND CAST(f.id AS VARCHAR) = d.parent_id AND (")
            .append("LOWER(COALESCE(f.user_creator, '')) = :member OR ")
            .append(memberOf("f", "nguoichinhsuahide")).append(" OR ").append(memberOf("f", "nguoixemhide"))
            .append(")))");
        appendKeyword(sql, params, keyword);
        return page(sql.toString(), params, pageable);
    }

    private static void appendKeyword(StringBuilder sql, Map<String, Object> params, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        sql.append(" AND td_search_norm(").append(KEYWORD_FIELDS).append(") LIKE :keyword");
        params.put("keyword", SearchTextSql.containsLike(keyword)); // không phân biệt dấu (V1.0.75)
    }

    private static String notBlank(String field) {
        return "TRIM(COALESCE(d.dynamic_fields->>'" + field + "', '')) <> ''";
    }

    private static String memberOf(String field) {
        return memberOf("d", field);
    }

    // Tên field là hằng số trong lớp (không từ client).
    private static String memberOf(String alias, String field) {
        return ":member = ANY(string_to_array(LOWER(REPLACE(COALESCE(" + alias + ".dynamic_fields->>'" + field
            + "', ''), ' ', '')), ';'))";
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    @SuppressWarnings("unchecked")
    private List<ChiaSeTepDocument> list(String sql, Map<String, Object> params) {
        Query query = entityManager.createNativeQuery(sql + " ORDER BY d.created_on ASC", ChiaSeTepDocument.class);
        params.forEach(query::setParameter);
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    private Page<ChiaSeTepDocument> page(String sql, Map<String, Object> params, Pageable pageable) {
        Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM (" + sql + ") AS matched");
        params.forEach(countQuery::setParameter);
        long total = ((Number) countQuery.getSingleResult()).longValue();
        Query query = entityManager.createNativeQuery(
            sql + " ORDER BY d.created_on DESC NULLS LAST LIMIT :limit OFFSET :offset", ChiaSeTepDocument.class);
        params.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());
        return new PageImpl<>(query.getResultList(), pageable, total);
    }
}
