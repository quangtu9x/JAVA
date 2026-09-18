package com.td.application.duthao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * BL-230 (DuThao Core) — DT-001 (tài liệu phân tích 04 mục 12): request cho
 * {@code POST /api/v1/duthao/XuLyDuThao}, tương thích {@code qlvb/duthao/xulyduthao} (Domino
 * {@code DraftProcesses.DraftProcessing()}).
 *
 * <p><b>Khác biệt CÓ CHỦ Ý so với wire Domino gốc</b>: Domino nhận {@code status}/{@code oldstatus}
 * do FE tự tính rồi gửi lên (xem {@code DuThaoActionModal.jsx#gettitle()}); ở đây SERVER tự suy ra
 * trạng thái đích từ {@link #action} (bảng ánh xạ cố định, đúng bảng {@code gettitle()}) và tự đọc
 * trạng thái HIỆN TẠI của document (cột {@code status}) làm "oldstatus" — không tin tham số phía
 * client. Lý do: (1) bảng action→status là 1-1 cố định phía Domino, không có lý do để client tự
 * tính rồi gửi lên; (2) tránh race condition nếu {@code resdata.status} phía FE bị cũ (xem phân
 * tích trong quá trình đối chiếu — Domino tự nó cũng có cơ chế {@code status_pre} riêng song song,
 * cho thấy chính Domino cũng nhận ra vấn đề này nhưng không dùng nhất quán). Ngoại lệ: action
 * {@code thuhoi} vẫn cần đọc "trạng thái trước đó" — ở đây đọc field {@code status_pre} đã lưu trên
 * chính document (server-side), thay vì tin {@code resdata?.status_pre} phía client.</p>
 */
@Data
public class XuLyDuThaoRequest {

    /** Id dự thảo (Domino: {@code parentid}). */
    @NotNull
    private UUID id;

    /**
     * Tên hành động — 1 trong: {@code chuyenduyet}, {@code chuyenkyduyet}, {@code chuyenkynhay},
     * {@code kyduyet}, {@code kynhay}, {@code tralai}, {@code chonnguoitralai} (alias
     * {@code tralai}), {@code thuhoi}, {@code chuyennguoikhac}, {@code chuyenphoihop} (alias
     * {@code chuyennguoikhac}), {@code ketthuc}, {@code danhgiaduthao}. KHÔNG hỗ trợ
     * {@code chuyenvanthu}/{@code thuhoichuyenvanthu} — 2 action đó đi qua endpoint riêng
     * ({@code ChuyenThanhVanBanDi}/{@code ThuHoiVanBanDi}, đã port trước Slice này).
     */
    @NotBlank
    private String action;

    /** Tên hiển thị người nhận — dùng cho chuyenduyet/chuyenkynhay/chuyennguoikhac/tralai. */
    private String receiver;

    /** Username người nhận (Domino: {@code receiver_username} trên document con {@code draft_process}). */
    private String receiverUsername;

    /** Ý kiến/ghi chú tự do — hiện CHƯA lưu ở đâu (document con {@code draft_process}/lịch sử xử lý
     * thuộc DT-007, chưa port) — chỉ nhận để tương thích payload FE, không có tác dụng field nào. */
    private String opinion;

    /** JSON thô "người chuyển" (Domino: {@code nguoichuyen_obj}) — copy nguyên văn vào dự thảo nếu có. */
    private String nguoiChuyenObj;

    /** Chỉ dùng cho action {@code danhgiaduthao} — giá trị đánh giá, ghi vào field {@code draft_rate}. */
    private String rating;
}
