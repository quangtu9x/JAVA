package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 18/09/2026 (cutover "Gửi xin ý kiến từ vbden" — {@code VBDenButtonActionXuLy.jsx#handleXinYKien}):
 * request cho {@code POST /api/v1/vbden/GuiXinYKienTuVBden} — tương thích API legacy
 * {@code qlvb/vbden/GuiXinYKienTuVBden} ({@code GuiXinYKienTuVBden.java} thật).
 *
 * <p>KHÔNG có field cho 5 cờ {@code is_tvub_opinion}/{@code is_tvnb_opinion}/{@code is_btv_opinion}/
 * {@code is_bch_opinion}/{@code is_ttdu_opinion} mà FE (dead code trước cutover) từng gửi — đọc lại
 * {@code GuiXinYKienTuVBden.java} thật (hàm {@code tao_vbden}, dòng ~232) xác nhận Domino gốc HARDCODE
 * {@code is_tvnb_opinion="true"} và KHÔNG BAO GIỜ đọc 5 cờ đó — port ĐÚNG hành vi thật (khác
 * {@link GuiXinYKienUseCase}, nhánh vbdi, có phân biệt TVUB/TVNB qua cờ {@code noibo}).</p>
 */
@Data
public class GuiXinYKienTuVBdenRequest {

    @NotNull(message = "idvbden khong duoc de trong")
    private UUID idvbden;

    private String content;

    /** Định dạng {@code dd/MM/yyyy} (hoặc rỗng) — FE tự chuẩn hoá trước khi gửi. */
    private String deadlineDate;

    /** JSON mảng {@code {identifier, username, xinykien}} — danh sách người nhận + trạng thái chọn. */
    private String listTvubObj;

    /** JSON mảng lịch sử chỉnh sửa danh sách (tuỳ chọn, {@code YKienTVUBTable.jsx} tự tích luỹ). */
    private String listEditTvubObj;
}
