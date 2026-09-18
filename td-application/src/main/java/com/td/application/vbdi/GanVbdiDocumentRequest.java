package com.td.application.vbdi;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 29/08/2026 (yêu cầu người dùng "gửi văn bản thay thế ... phân tích và thực hiện port luôn"):
 * request cho {@code POST /api/v1/vbdi/GanVanBanDi} — port {@code DocumentOutProcesses.java#
 * add_Document_Revoke()} (Domino, endpoint {@code qlvb/vbdi/adddocrevoke}), nút FE "Gắn VB đi"
 * ({@code GanVanBanModal.jsx#btnActionGanVanBanDiRevoke}/{@code DocumentInfo.jsx} nút "xoá").
 *
 * <p>{@code documentId} = văn bản B (đang mở, thường là văn bản MỚI thay thế/thu hồi văn bản khác).
 * {@code replacesDocumentId} = văn bản A (văn bản BỊ thay thế/thu hồi) — truyền {@code null} để GỠ
 * liên kết hiện có (Domino: {@code isdelete=true}). CHỈ 1 liên kết/văn bản (khớp giới hạn FE "Chỉ
 * được phép gắn 1 văn bản", {@code GanVanBanModal.jsx} dòng 83-85) — gọi lại API này với
 * {@code replacesDocumentId} khác sẽ THAY liên kết cũ, không cộng dồn.</p>
 */
@Data
public class GanVbdiDocumentRequest {

    @NotNull(message = "documentId khong duoc de trong")
    private UUID documentId;

    /** {@code null} = gỡ liên kết hiện có (nếu có). */
    private UUID replacesDocumentId;
}
