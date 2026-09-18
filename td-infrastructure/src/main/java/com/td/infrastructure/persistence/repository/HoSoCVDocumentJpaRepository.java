package com.td.infrastructure.persistence.repository;

import com.td.domain.hosocv.HoSoCVDocument;

/**
 * Mở rộng BL-240 (HoSoCV Core) — bảng vật lý ĐỘC LẬP {@code hosocv_documents}, KHÔNG phải
 * {@link DocumentJpaRepository} (bảng {@code documents} dùng chung).
 */
public interface HoSoCVDocumentJpaRepository extends BaseRepository<HoSoCVDocument> {
}
