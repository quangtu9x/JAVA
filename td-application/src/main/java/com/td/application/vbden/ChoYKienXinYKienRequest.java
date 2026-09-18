package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * N08 (31/08/2026 — "cho ý kiến" TVUB/TVNB/BCH/BTV/TTĐU): request cho
 * {@code POST /api/v1/vbden/ChoYKien} — tương thích API legacy {@code qlvb/vbden/ChuyenXuLy?action=choykien},
 * nhưng tách RIÊNG endpoint (không dùng chung {@code ChuyenXuLyVbdenRequest}/{@code ChuyenXuLyVbdenUseCase})
 * vì đối tượng xử lý là {@code document_type="vbden_xinykien"} ("văn bản đến giả"), không phải văn
 * bản đến thật — xem javadoc {@link ChoYKienXinYKienUseCase}.
 */
@Data
public class ChoYKienXinYKienRequest {

    @NotNull(message = "parentid khong duoc de trong")
    private UUID parentid;

    /**
     * 01/09/2026 (đối chiếu trực tiếp {@code TraLoiVanBanXinYKien.java} thật + FE
     * {@code ChoYKienTVUB.jsx} — 2 nút "Đồng ý"/"Không đồng ý"): giá trị NÀY (không phải
     * {@link #opinion}) mới là cái Domino ghi vào field động tên=username trên văn bản cha
     * ({@code docVBden.replaceItemValue(username, docQTXL.status)}) và tích luỹ vào {@code tralois}
     * — mặc định "Đồng ý" nếu để trống (khớp {@code DEFAULT_OPINION} cũ, tương thích ngược FE chưa
     * gửi field này).
     */
    private String status;

    /**
     * Ý kiến/ghi chú TỰ DO (tuỳ chọn) — Domino lưu field {@code opinion} RIÊNG trên document con
     * (khác {@link #status}, KHÔNG ghi lên văn bản cha) — chỉ đọc lại được qua bản ghi con, không
     * hiển thị ở field tên=username hay {@code tralois}.
     */
    private String opinion;
}
