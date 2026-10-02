package com.td.application.vbdi;

import com.td.domain.vbdi.VbdiEditHistoryDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026: port truy cập bảng vật lý ĐỘC LẬP
 * {@code vbdi_edit_history_documents} (vết hiệu chỉnh field/tệp đính kèm văn bản đi, xem
 * {@link VbdiEditHistoryDocument}) — mirror {@code DuThaoEditHistoryDocumentRepository}.
 */
public interface VbdiEditHistoryDocumentRepository {

    <S extends VbdiEditHistoryDocument> S save(S entity);

    /**
     * Danh sách vết hiệu chỉnh theo văn bản đi cha. Thứ tự TẠO TĂNG DẦN (created_on ASC — insert
     * order): khớp response Domino {@code GetRes} mà {@code VetHieuChinh.jsx} đang REVERSE trước
     * khi render (để hiện mới nhất trên cùng) — giữ nguyên thứ tự nguồn để UI không đổi hành vi.
     // 25/09/2026: MỚI NHẤT TRƯỚC (created_on DESC) — FE VetHieuChinh.jsx không còn reverse; javadoc trên mô tả hành vi cũ.
     */
    Page<VbdiEditHistoryDocument> findByParentIdNewestFirst(UUID parentId, Pageable pageable);
}
