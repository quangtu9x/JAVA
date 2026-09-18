package com.td.application.duthao;

import com.td.domain.duthao.DuThaoEditHistoryDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Yêu cầu người dùng 27/08/2026: port truy cập bảng vật lý ĐỘC LẬP
 * {@code duthao_edit_history_documents} (vết hiệu chỉnh field dự thảo, xem
 * {@link DuThaoEditHistoryDocument}) — KHÔNG dùng lại {@link DuThaoDocumentRepository}/
 * {@link DraftProcessDocumentRepository} (mỗi bảng vật lý 1 repository riêng, đúng tiền lệ đã làm
 * với các lần tách trước).
 */
public interface DuThaoEditHistoryDocumentRepository {

    <S extends DuThaoEditHistoryDocument> S save(S entity);

    /**
     * Danh sách vết hiệu chỉnh theo dự thảo cha. Thứ tự TẠO TĂNG DẦN (created_on ASC — insert
     * order): khớp response DOMINO {@code GetRes} mà {@code VetHieuChinh.jsx} đang REVERSE trước
     * khi render (để hiện mới nhất trên cùng) — giữ nguyên thứ tự nguồn để UI không đổi hành vi.
     */
    Page<DuThaoEditHistoryDocument> findByParentIdAsc(UUID parentId, Pageable pageable);
}
