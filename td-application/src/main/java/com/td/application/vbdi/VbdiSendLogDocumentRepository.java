package com.td.application.vbdi;

import com.td.domain.vbdi.VbdiSendLogDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026 ("tách lưu Nhật ký gửi nhận ra bảng vật lý khác như làm với
 * draft_process"): port truy cập bảng vật lý ĐỘC LẬP {@code vbdi_send_log_documents} (xem
 * {@link VbdiSendLogDocument}) — KHÔNG dùng lại {@link VbdiDocumentRepository} (bảng
 * {@code vbdi_documents}, document chính). Chỉ khai báo ĐÚNG method thực sự cần (đúng tiền lệ
 * {@code DraftProcessDocumentRepository}/{@code DuThaoEditHistoryDocumentRepository} — không kế
 * thừa {@code IRepository<T>} vì bảng con append-only không cần findById/delete).
 */
public interface VbdiSendLogDocumentRepository {

    <S extends VbdiSendLogDocument> S save(S entity);

    /**
     * Lịch sử gửi/nhận theo văn bản đi cha, MỚI NHẤT TRƯỚC ({@code created_on DESC}) — hợp lý cho
     * 1 log/feed hiển thị, đúng chiều {@code DraftProcessDocumentRepository#findByParentId} (khác
     * {@code DuThaoEditHistoryDocumentRepository#findByParentIdAsc} vốn ASC chỉ vì phải khớp hành
     * vi FE cũ {@code VetHieuChinh.jsx} tự reverse — ở đây là tính năng MỚI, không có ràng buộc
     * tương thích ngược nào bắt phải ASC).
     */
    Page<VbdiSendLogDocument> findByParentId(UUID parentId, Pageable pageable);

    /**
     * Dedup cho {@code send_type="senddocument"} (gửi lần đầu): TRUE nếu nơi nhận này (nhóm +
     * định danh) đã có ÍT NHẤT 1 vết gửi trước đó cho văn bản này — tránh ghi trùng vết "Gửi văn
     * bản" ban đầu cho cùng 1 nơi nhận (đúng tinh thần "dedup theo identifier" của Domino gốc,
     * §2 điểm 2 tài liệu phân tích 07 — khác Domino ở chỗ KHÔNG "setAll cập nhật" bản ghi cũ vì
     * bảng này append-only, chỉ đơn giản là KHÔNG ghi thêm vết trùng). Không áp dụng cho
     * {@code resenddocument}/{@code guibosung}/{@code guilaitoanbo} (luôn ghi vết mới - xem javadoc
     * {@code SendVbdiDocumentUseCase}).
     */
    boolean existsByParentIdAndRecipientGroupAndRecipientIdentifier(
        UUID parentId, String recipientGroup, String recipientIdentifier);
}
