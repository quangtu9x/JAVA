package com.td.application.vbdi;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 11/09/2026 (yêu cầu người dùng "danh sách... chức năng xóa chưa cutover FE"): request cho
 * {@code POST /api/v1/vbdi/XoaVanBanLienQuan} — port {@code VanBanLienQuanList.jsx#handleDelete} khi
 * danh sách "Văn bản kèm theo" thuộc văn bản đi (xem javadoc {@link XoaVanBanLienQuanUseCase}).
 *
 * <p>{@code documentId}: {@code id} của phần tử cần loại khỏi {@code vanban_lienquan_obj} — KIỂU
 * STRING (không phải UUID) vì phần tử có thể là UUID Postgres HOẶC UNID Domino (32 hex), cùng lý do
 * {@link GanVanBanLienQuanRequest#getRelatedDocuments()}.</p>
 */
@Data
public class XoaVanBanLienQuanRequest {

    @NotNull(message = "vbdiId khong duoc de trong")
    private UUID vbdiId;

    @NotBlank(message = "Thieu id van ban can xoa")
    private String documentId;
}
