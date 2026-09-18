package com.td.infrastructure.persistence.repository;

import com.td.application.hosoluutru.HoSoLuuTruDanhMucDocumentRepository;
import com.td.domain.hosoluutru.HoSoLuuTruDanhMucDocument;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port module "Hồ sơ lưu trữ" — adapter cho bảng vật lý ĐỘC LẬP {@code hosoluutru_danh_muc_documents}.
 */
@Repository
public class PostgresHoSoLuuTruDanhMucDocumentRepository implements HoSoLuuTruDanhMucDocumentRepository {

    private static final int MAX_ROWS = 2000;

    private final HoSoLuuTruDanhMucDocumentJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresHoSoLuuTruDanhMucDocumentRepository(HoSoLuuTruDanhMucDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<HoSoLuuTruDanhMucDocument> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<HoSoLuuTruDanhMucDocument, UUID>)
                jpaRepository).findById(id);
    }

    @Override
    public <S extends HoSoLuuTruDanhMucDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public void delete(HoSoLuuTruDanhMucDocument entity) {
        jpaRepository.delete(entity);
    }

    @Override
    public List<HoSoLuuTruDanhMucDocument> findByDocumentType(String documentType) {
        String sql = """
            SELECT d.*
            FROM hosoluutru_danh_muc_documents d
            WHERE d.deleted_on IS NULL
              AND LOWER(COALESCE(d.document_type, '')) = :documentType
            ORDER BY CASE WHEN (d.dynamic_fields->>'sort_order') ~ '^[0-9]+$'
                          THEN (d.dynamic_fields->>'sort_order')::int
                          ELSE 999999 END,
                     d.created_on ASC
            LIMIT :limit
            """;
        Query query = entityManager.createNativeQuery(sql, HoSoLuuTruDanhMucDocument.class);
        query.setParameter("documentType", documentType == null ? "" : documentType.trim().toLowerCase());
        query.setParameter("limit", MAX_ROWS);

        @SuppressWarnings("unchecked")
        List<HoSoLuuTruDanhMucDocument> result = query.getResultList();
        return result;
    }
}
