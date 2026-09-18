package com.td.application.duthao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 16/09/2026 (yêu cầu người dùng "Văn bản kèm theo -> ... chưa port"): request cho
 * {@code POST /api/v1/duthao/XoaVanBanLienQuan} — port {@code VanBanLienQuanList.jsx#handleDelete}
 * khi mở TỪ chi tiết dự thảo (nhánh {@code databasetype==="duthao"}, trước đây CHƯA có Java tương
 * ứng nên vẫn rơi vào {@code qlvb/add} cũ) — xem javadoc {@link XoaVanBanLienQuanDuThaoUseCase}.
 *
 * <p>{@code documentId}: {@code id} của phần tử cần loại khỏi {@code vanban_lienquan_obj} — KIỂU
 * STRING (không phải UUID) vì phần tử có thể là UUID Postgres HOẶC UNID Domino (32 hex).</p>
 */
@Data
public class XoaVanBanLienQuanDuThaoRequest {

    @NotNull(message = "draftId khong duoc de trong")
    private UUID draftId;

    @NotBlank(message = "Thieu id van ban can xoa")
    private String documentId;
}
