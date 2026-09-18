package com.td.infrastructure.persistence.repository;

import com.td.application.phieutrinhtd.PhieuTrinhTdProcessDocumentRepository;
import com.td.domain.phieutrinhtd.PhieuTrinhTdProcessDocument;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter cho bảng vật lý ĐỘC LẬP {@code phieutrinhtd_process_documents} (nhật ký "quatrinh").
 */
@Repository
public class PostgresPhieuTrinhTdProcessDocumentRepository implements PhieuTrinhTdProcessDocumentRepository {

    private final PhieuTrinhTdProcessDocumentJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresPhieuTrinhTdProcessDocumentRepository(PhieuTrinhTdProcessDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<PhieuTrinhTdProcessDocument> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<PhieuTrinhTdProcessDocument, UUID>) jpaRepository)
            .findById(id);
    }

    @Override
    public <S extends PhieuTrinhTdProcessDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public void delete(PhieuTrinhTdProcessDocument entity) {
        jpaRepository.delete(entity);
    }

    @Override
    public List<PhieuTrinhTdProcessDocument> findByParentId(UUID parentId) {
        String sql = """
            SELECT d.*
            FROM phieutrinhtd_process_documents d
            WHERE d.deleted_on IS NULL
              AND d.dynamic_fields @> CAST(:parentIdFilter AS jsonb)
            ORDER BY d.created_on DESC
            """;
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("parentIdFilter", "{\"parentid\": \"" + (parentId == null ? "" : parentId) + "\"}");

        Query query = entityManager.createNativeQuery(sql, PhieuTrinhTdProcessDocument.class);
        parameters.forEach(query::setParameter);

        @SuppressWarnings("unchecked")
        List<PhieuTrinhTdProcessDocument> result = query.getResultList();
        return result;
    }
}
