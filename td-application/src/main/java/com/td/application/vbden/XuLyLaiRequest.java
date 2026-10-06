package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * N10 (01/09/2026 — "Xử lý lại" văn bản đến, xem javadoc {@link XuLyLaiUseCase}): request cho
 * {@code POST /api/v1/vbden/XuLyLai} — tương thích API legacy {@code qlvb/vbden/XuLyLai?
 * databasetype=vbden&action=xulylai&parentid=...} ({@code XuLyLai.java} thật). Khác Domino (đọc
 * {@code parentid} từ query string, {@code opinion} từ FormData body) — Java gộp cả 2 vào 1 JSON
 * body duy nhất, không có sự mơ hồ 2-nguồn.
 */
@Data
public class XuLyLaiRequest {

    @NotNull(message = "parentid khong duoc de trong")
    private UUID parentid;

    /** Lý do "xử lý lại" — FE ({@code ItemsActiveList.jsx#thuhoivanban}) BẮT BUỘC nhập trước khi gửi. */
    private String opinion;

    /** 05/10/2026: tên hiển thị người thực hiện (tuỳ chọn, server vẫn ưu tiên tên trong hồ sơ). */
    private String creatorName;

    /** 05/10/2026: chức danh người thực hiện (tuỳ chọn, rỗng thì server lấy từ hồ sơ tổ chức). */
    private String position;
}
