package com.td.application.vbden;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BL-210/211 (MVP, 21/08/2026) — request cho {@code POST /api/v1/vbden/Luu}, tương thích 1 phần
 * (xem javadoc {@link LuuVanBanDenUseCase}) payload legacy {@code qlvb/vbden?action=luu} khi
 * {@code id} có giá trị (hiệu chỉnh / "vào sổ" văn bản đến ĐÃ TỒN TẠI — khác nhánh tạo mới hoàn
 * toàn của {@link TiepNhanVbdenUseCase}).
 */
@Data
public class LuuVanBanDenRequest {

    /** Id văn bản đến gốc cần cập nhật (bắt buộc — {@code idvbden} phía FE). */
    private UUID id;

    /**
     * Id bản ghi "sổ" ĐÃ TỒN TẠI nếu văn bản này đã từng được "vào sổ" trước đó (hiệu chỉnh lại sổ)
     * — để trống nếu đây là lần "vào sổ" đầu tiên hoặc không đổi gì về sổ.
     */
    private UUID idso;

    /**
     * Sổ văn bản được chọn (nếu người dùng đang "vào sổ" hoặc đổi sổ) — để trống nếu chỉ hiệu chỉnh
     * field thông thường, không liên quan tới sổ.
     */
    private UUID documentRegisterId;

    private Map<String, String> fields = new LinkedHashMap<>();

    private String dateFields;

    private String numberFields;

    private String richtextFields;
}
