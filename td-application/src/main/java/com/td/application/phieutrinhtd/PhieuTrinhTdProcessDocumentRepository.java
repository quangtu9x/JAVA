package com.td.application.phieutrinhtd;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.phieutrinhtd.PhieuTrinhTdProcessDocument;

import java.util.List;
import java.util.UUID;

/**
 * Nhật ký "quatrinh" của phiếu trình thẩm định — bảng vật lý ĐỘC LẬP
 * {@code phieutrinhtd_process_documents} (migration V1.0.54). Xem
 * {@code docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md} mục 6.1/6.2.
 */
public interface PhieuTrinhTdProcessDocumentRepository extends IRepository<PhieuTrinhTdProcessDocument> {

    /** Toàn bộ log của 1 phiếu trình (field động {@code parentid}), mới nhất trước. */
    List<PhieuTrinhTdProcessDocument> findByParentId(UUID parentId);
}
