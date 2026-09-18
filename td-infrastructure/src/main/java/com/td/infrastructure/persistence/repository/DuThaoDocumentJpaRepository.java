package com.td.infrastructure.persistence.repository;

import com.td.domain.duthao.DuThaoDocument;

/**
 * BL-230 (DuThao Core), yêu cầu người dùng 24/08/2026 — bảng vật lý ĐỘC LẬP {@code duthao_documents},
 * KHÔNG phải {@link DocumentJpaRepository} (bảng {@code documents} dùng chung).
 */
public interface DuThaoDocumentJpaRepository extends BaseRepository<DuThaoDocument> {
}
