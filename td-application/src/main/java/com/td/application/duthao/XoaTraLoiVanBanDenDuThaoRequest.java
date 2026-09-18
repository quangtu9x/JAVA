package com.td.application.duthao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 16/09/2026 (yêu cầu người dùng "đã gắn được, nhưng nút xóa ở danh sách chưa port, đang gọi api cũ
 * qlvb/add?id=...&databasetype=duthao"): request cho {@code POST /api/v1/duthao/XoaTraLoiVanBanDen}
 * — port {@code TraLoiChoVanBanDen.jsx#handleDelete} khi mở TỪ chi tiết dự thảo (khác nhánh
 * {@code databasetype!=="duthao"} đã port trước đó cho văn bản đi ở
 * {@link com.td.application.vbdi.XoaTraLoiVanBanDenUseCase}) — xem javadoc
 * {@link XoaTraLoiVanBanDenDuThaoUseCase}.
 *
 * <p>{@code documentId}: {@code id} của phần tử cần loại khỏi {@code documentin_obj} — KIỂU STRING
 * (không phải UUID), cùng lý do {@code XoaVanBanLienQuanRequest#getDocumentId()} (phần tử có thể là
 * UUID Postgres hoặc UNID Domino).</p>
 */
@Data
public class XoaTraLoiVanBanDenDuThaoRequest {

    @NotNull(message = "draftId khong duoc de trong")
    private UUID draftId;

    @NotBlank(message = "Thieu id van ban can xoa")
    private String documentId;
}
