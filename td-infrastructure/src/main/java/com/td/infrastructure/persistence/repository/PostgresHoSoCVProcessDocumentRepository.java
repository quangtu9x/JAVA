package com.td.infrastructure.persistence.repository;

import com.td.application.hosocv.HoSoCVProcessDocumentRepository;
import com.td.domain.hosocv.HoSoCVProcessDocument;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mở rộng BL-240 (HoSoCV Core) — adapter cho bảng vật lý ĐỘC LẬP {@code hosocv_process_documents}.
 */
@Repository
public class PostgresHoSoCVProcessDocumentRepository implements HoSoCVProcessDocumentRepository {

    private static final int MAX_HISTORY_ROWS = 500;

    private final HoSoCVProcessDocumentJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresHoSoCVProcessDocumentRepository(HoSoCVProcessDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<HoSoCVProcessDocument> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<HoSoCVProcessDocument, UUID>) jpaRepository)
            .findById(id);
    }

    @Override
    public <S extends HoSoCVProcessDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public void delete(HoSoCVProcessDocument entity) {
        jpaRepository.delete(entity);
    }

    @Override
    public List<HoSoCVProcessDocument> findByHoSoCVId(UUID hosocvId) {
        String sql = """
            SELECT d.*
            FROM hosocv_process_documents d
            WHERE d.deleted_on IS NULL
              AND COALESCE(d.dynamic_fields->>'hosocv_id', '') = :hosocvId
            ORDER BY d.created_on ASC
            LIMIT :limit
            """;
        Query query = entityManager.createNativeQuery(sql, HoSoCVProcessDocument.class);
        query.setParameter("hosocvId", hosocvId.toString());
        query.setParameter("limit", MAX_HISTORY_ROWS);

        @SuppressWarnings("unchecked")
        List<HoSoCVProcessDocument> result = query.getResultList();
        return result;
    }
}
