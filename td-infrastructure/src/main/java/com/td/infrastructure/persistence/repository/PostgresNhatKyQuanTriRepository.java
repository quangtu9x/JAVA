package com.td.infrastructure.persistence.repository;

import com.td.application.nhatkyhethong.NhatKyQuanTriRepository;
import com.td.domain.edithistory.EditHistoryDocument;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** 30/10/2026 (port "Nhật ký quản trị"): đọc {@code edit_history_documents} theo form_edit - SQL native. */
@Repository
public class PostgresNhatKyQuanTriRepository implements NhatKyQuanTriRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public Page<EditHistoryDocument> search(String formEdit, String keyword, LocalDate from, LocalDate to,
                                            Pageable pageable) {
        StringBuilder sql = new StringBuilder("SELECT d.* FROM edit_history_documents d WHERE d.deleted_on IS NULL");
        Map<String, Object> params = new LinkedHashMap<>();
        if (formEdit != null && !formEdit.isBlank()) {
            sql.append(" AND LOWER(COALESCE(d.form_edit, '')) = :formEdit");
            params.put("formEdit", formEdit.trim().toLowerCase());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND td_search_norm(COALESCE(d.creator_name, '') || ' ' || COALESCE(d.user_creator, '')")
                .append(" || ' ' || CAST(d.edit_obj AS TEXT)) LIKE :keyword");
            params.put("keyword", SearchTextSql.containsLike(keyword));
        }
        if (from != null) {
            sql.append(" AND d.created_on >= :fromDate");
            params.put("fromDate", from.atStartOfDay());
        }
        if (to != null) {
            sql.append(" AND d.created_on < :toDate");
            params.put("toDate", to.plusDays(1).atStartOfDay());
        }
        Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM (" + sql + ") AS matched");
        params.forEach(countQuery::setParameter);
        long total = ((Number) countQuery.getSingleResult()).longValue();
        Query query = entityManager.createNativeQuery(
            sql + " ORDER BY d.created_on DESC LIMIT :limit OFFSET :offset", EditHistoryDocument.class);
        params.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());
        return new PageImpl<>(query.getResultList(), pageable, total);
    }
}
