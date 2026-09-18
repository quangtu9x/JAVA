package com.td.infrastructure.persistence.repository;

import com.td.domain.vbcddh.VbcddhDocument;

/**
 * BL-250 (VBCDDH Core), bổ sung 08/09/2026 (yêu cầu người dùng "tạo bảng riêng") — bảng vật lý ĐỘC
 * LẬP {@code vbcddh_documents}, KHÔNG phải {@link DocumentJpaRepository} (bảng {@code documents}
 * dùng chung).
 */
public interface VbcddhDocumentJpaRepository extends BaseRepository<VbcddhDocument> {
}
