package com.td.application.duthao;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * DT-007 (tiếp, 26/08/2026) — request cho {@code POST /api/v1/duthao/GanKemTep}. Tương đương
 * nhánh "xử lý đính kèm" của {@code ChiTietPage.jsx#onFinish} (Domino gốc: {@code qlvb/add?
 * databasetype=duthao&parentid=...} với {@code Form=draft_process}, {@code action_type=gankemtep})
 * — ghi 1 vết {@code draft_process} mang ý kiến/đánh dấu đính kèm tệp mới khi Lưu dự thảo, TÁCH
 * BIỆT với việc lưu field lõi ({@code TaoDuThao}/{@code Luu}) và đính kèm tệp vào CHÍNH dự thảo
 * ({@code POST /api/v1/documents/{id}/files}).
 */
@Data
public class GanKemTepDuThaoRequest {

    /** id dự thảo cha (đã tồn tại — tạo mới xong mới gọi endpoint này). */
    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    /** Ý kiến người dùng gõ khi Lưu (tuỳ chọn — file đính kèm cũng có thể là lý do gọi 1 mình). */
    private String opinion;
}
