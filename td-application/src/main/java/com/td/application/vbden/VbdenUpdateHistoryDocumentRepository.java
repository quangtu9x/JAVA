package com.td.application.vbden;

import com.td.domain.vbden.VbdenUpdateHistoryDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Yêu cầu người dùng 24/09/2026: truy cập bảng {@code vbden_update_history_documents} (lịch sử "Cập
 * nhật VB", xem {@link VbdenUpdateHistoryDocument}) — mirror {@link VbdenEditHistoryDocumentRepository}.
 */
public interface VbdenUpdateHistoryDocumentRepository {

    <S extends VbdenUpdateHistoryDocument> S save(S entity);

    /** Lịch sử theo văn bản đến ĐÍCH, MỚI NHẤT TRƯỚC (created_on DESC) — hiển thị dạng dòng thời gian. */
    Page<VbdenUpdateHistoryDocument> findByParentIdDesc(UUID parentId, Pageable pageable);
}
