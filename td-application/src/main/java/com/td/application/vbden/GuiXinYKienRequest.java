package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * N08 (31/08/2026 — "cho ý kiến" TVUB/TVNB/BCH/BTV/TTĐU): request cho
 * {@code POST /api/v1/vbden/GuiXinYKien} — tương thích API legacy {@code qlvb/vbden/GuiXinYKien}/
 * {@code qlvb/vbden/GuiXinYKienNoiBo} (2 URL Domino gộp thành 1 endpoint + cờ {@code noibo}, xem
 * {@code ChiTietModal.jsx#handleGuiXinYKien}/{@code handleGuiXinYKienTVNB} — payload THẬT chỉ có
 * đúng {@code idvbdi} (+ {@code noibo} cho biến thể TVNB), KHÔNG có danh sách người nhận — server tự
 * đọc {@code list_tvub_obj}/{@code list_tvnb_obj} đã có sẵn trên chính document vbdi, xem javadoc
 * {@link GuiXinYKienUseCase}).
 */
@Data
public class GuiXinYKienRequest {

    @NotNull(message = "idvbdi khong duoc de trong")
    private UUID idvbdi;

    /** {@code true} = biến thể "nội bộ" (TVNB, đọc {@code list_tvnb_obj}/cờ {@code is_tvnb_opinion}). */
    private boolean noibo;
}
