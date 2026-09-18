package com.td.infrastructure.persistence.repository;

import com.td.domain.vbden.VbdenResDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * BL-210 (VBDen Core) — DoD (4), yêu cầu người dùng 05/09/2026 ("tách vbden_res sang bảng riêng"):
 * Spring Data gateway cho bảng vật lý ĐỘC LẬP {@code vbden_res_documents} (xem
 * {@link VbdenResDocument}) — tách khỏi {@link DocumentJpaRepository} (bảng {@code documents}) theo
 * đúng khuôn {@link DraftProcessDocumentJpaRepository} đã làm.
 */
@Repository
public interface VbdenResDocumentJpaRepository extends JpaRepository<VbdenResDocument, UUID> {
}
