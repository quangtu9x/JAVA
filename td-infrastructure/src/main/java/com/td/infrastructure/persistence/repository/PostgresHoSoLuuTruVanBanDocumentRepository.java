package com.td.infrastructure.persistence.repository;

import com.td.application.hosoluutru.HoSoLuuTruVanBanDocumentRepository;
import com.td.domain.hosoluutru.HoSoLuuTruVanBanDocument;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port module "Hồ sơ lưu trữ" — adapter cho bảng vật lý ĐỘC LẬP {@code hosoluutru_van_ban_documents}.
 */
@Repository
public class PostgresHoSoLuuTruVanBanDocumentRepository implements HoSoLuuTruVanBanDocumentRepository {

    private static final int MAX_ROWS = 2000;

    private final HoSoLuuTruVanBanDocumentJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresHoSoLuuTruVanBanDocumentRepository(HoSoLuuTruVanBanDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<HoSoLuuTruVanBanDocument> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<HoSoLuuTruVanBanDocument, UUID>) jpaRepository)
            .findById(id);
    }

    @Override
    public <S extends HoSoLuuTruVanBanDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public void delete(HoSoLuuTruVanBanDocument entity) {
        jpaRepository.delete(entity);
    }

    @Override
    public List<HoSoLuuTruVanBanDocument> findByHosoId(UUID hosoId) {
        String sql = """
            SELECT d.*
            FROM hosoluutru_van_ban_documents d
            WHERE d.deleted_on IS NULL
              AND COALESCE(d.dynamic_fields->>'hoso_id', '') = :hosoId
            ORDER BY d.created_on ASC
            LIMIT :limit
            """;
        Query query = entityManager.createNativeQuery(sql, HoSoLuuTruVanBanDocument.class);
        query.setParameter("hosoId", hosoId.toString());
        query.setParameter("limit", MAX_ROWS);

        @SuppressWarnings("unchecked")
        List<HoSoLuuTruVanBanDocument> result = query.getResultList();
        return result;
    }

    @Override
    public List<HoSoLuuTruVanBanDocument> findDeletedByHosoId(UUID hosoId) {
        String sql = """
            SELECT d.*
            FROM hosoluutru_van_ban_documents d
            WHERE d.deleted_on IS NOT NULL
              AND COALESCE(d.dynamic_fields->>'hoso_id', '') = :hosoId
            ORDER BY d.created_on ASC
            LIMIT :limit
            """;
        Query query = entityManager.createNativeQuery(sql, HoSoLuuTruVanBanDocument.class);
        query.setParameter("hosoId", hosoId.toString());
        query.setParameter("limit", MAX_ROWS);

        @SuppressWarnings("unchecked")
        List<HoSoLuuTruVanBanDocument> result = query.getResultList();
        return result;
    }
}
