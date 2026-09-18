package com.td.infrastructure.persistence.repository;

import com.td.application.numbering.NumberingCounterRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

/**
 * BL-104: tăng bộ đếm bằng {@code INSERT ... ON CONFLICT DO UPDATE ... RETURNING} — 1 câu lệnh
 * SQL nguyên tử duy nhất (row lock của Postgres đảm bảo an toàn dưới concurrent access), vừa xử
 * lý trường hợp bộ đếm chưa tồn tại (năm mới → bắt đầu từ 1) vừa tăng nguyên tử khi đã tồn tại,
 * không cần pessimistic lock/retry ở tầng ứng dụng.
 */
@Repository
public class PostgresNumberingCounterRepository implements NumberingCounterRepository {

    private static final String UPSERT_AND_RETURN_SQL = """
        INSERT INTO numbering_counters (id, document_type, year, scope_key, current_value, updated_at)
        VALUES (uuid_generate_v4(), :documentType, :year, :scopeKey, 1, NOW())
        ON CONFLICT (document_type, year, scope_key)
        DO UPDATE SET current_value = numbering_counters.current_value + 1, updated_at = NOW()
        RETURNING current_value
        """;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public long incrementAndGet(String documentType, int year, String scopeKey) {
        Query query = entityManager.createNativeQuery(UPSERT_AND_RETURN_SQL);
        query.setParameter("documentType", documentType);
        query.setParameter("year", year);
        query.setParameter("scopeKey", scopeKey);
        return ((Number) query.getSingleResult()).longValue();
    }
}
