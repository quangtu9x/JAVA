package com.td.infrastructure.persistence.repository;

import com.td.domain.duthao.DraftProcessDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * DT-007 (tách draft_process sang bảng riêng, yêu cầu người dùng 25/08/2026): Spring Data gateway
 * cho bảng vật lý ĐỘC LẬP {@code draft_process_documents} (xem {@link DraftProcessDocument}) —
 * tách khỏi {@link DuThaoDocumentJpaRepository} (bảng {@code duthao_documents}) theo đúng khuôn
 * {@link VbdiDocumentJpaRepository}/{@code DuThaoDocumentJpaRepository} đã làm.
 */
@Repository
public interface DraftProcessDocumentJpaRepository extends JpaRepository<DraftProcessDocument, UUID> {
}