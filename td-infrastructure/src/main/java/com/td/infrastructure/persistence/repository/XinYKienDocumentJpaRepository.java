package com.td.infrastructure.persistence.repository;

import com.td.domain.vbden.XinYKienDocument;

/**
 * N08 ("cho ý kiến"), yêu cầu người dùng 31/08/2026 — bảng vật lý ĐỘC LẬP
 * {@code vbden_xinykien_documents}, KHÔNG phải {@link DocumentJpaRepository} (bảng {@code documents}
 * dùng chung, nơi vbden GỐC vẫn đang lưu).
 */
public interface XinYKienDocumentJpaRepository extends BaseRepository<XinYKienDocument> {
}
