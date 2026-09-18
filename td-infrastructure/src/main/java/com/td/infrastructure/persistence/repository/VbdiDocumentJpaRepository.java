package com.td.infrastructure.persistence.repository;

import com.td.domain.vbdi.VbdiDocument;

/**
 * BL-220 (VBDi Core), yêu cầu người dùng (tách văn bản đi khỏi bảng dùng chung) — bảng vật lý ĐỘC
 * LẬP {@code vbdi_documents}, KHÔNG phải {@link DocumentJpaRepository} (bảng {@code documents} dùng
 * chung).
 */
public interface VbdiDocumentJpaRepository extends BaseRepository<VbdiDocument> {
}