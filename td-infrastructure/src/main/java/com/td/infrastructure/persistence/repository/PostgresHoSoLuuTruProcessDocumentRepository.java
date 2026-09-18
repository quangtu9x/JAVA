package com.td.infrastructure.persistence.repository;

import com.td.application.hosoluutru.HoSoLuuTruProcessDocumentRepository;
import com.td.domain.hosoluutru.HoSoLuuTruProcessDocument;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port module "Hồ sơ lưu trữ" — adapter cho bảng vật lý ĐỘC LẬP {@code hosoluutru_process_documents}.
 */
@Repository
public class PostgresHoSoLuuTruProcessDocumentRepository implements HoSoLuuTruProcessDocumentRepository {

    private static final int MAX_HISTORY_ROWS = 500;

    private final HoSoLuuTruProcessDocumentJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresHoSoLuuTruProcessDocumentRepository(HoSoLuuTruProcessDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<HoSoLuuTruProcessDocument> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<HoSoLuuTruProcessDocument, UUID>)
                jpaRepository).findById(id);
    }

    @Override
    public <S extends HoSoLuuTruProcessDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public void delete(HoSoLuuTruProcessDocument entity) {
        jpaRepository.delete(entity);
    }

    @Override
    public List<HoSoLuuTruProcessDocument> findByHosoId(UUID hosoId) {
        String sql = """
            SELECT d.*
            FROM hosoluutru_process_documents d
            WHERE d.deleted_on IS NULL
              AND COALESCE(d.dynamic_fields->>'hoso_id', '') = :hosoId
            ORDER BY d.created_on ASC
            LIMIT :limit
            """;
        Query query = entityManager.createNativeQuery(sql, HoSoLuuTruProcessDocument.class);
        query.setParameter("hosoId", hosoId.toString());
        query.setParameter("limit", MAX_HISTORY_ROWS);

        @SuppressWarnings("unchecked")
        List<HoSoLuuTruProcessDocument> result = query.getResultList();
        return result;
    }
}
