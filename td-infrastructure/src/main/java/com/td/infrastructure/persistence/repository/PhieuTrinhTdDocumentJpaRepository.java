package com.td.infrastructure.persistence.repository;

import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;

/**
 * Port module "Phiếu trình thẩm định" — bảng vật lý ĐỘC LẬP {@code phieutrinhtd_documents}, KHÔNG phải
 * {@link DocumentJpaRepository} (bảng {@code documents} dùng chung).
 */
public interface PhieuTrinhTdDocumentJpaRepository extends BaseRepository<PhieuTrinhTdDocument> {
}
