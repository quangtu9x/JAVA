package com.td.application.vbdi;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 11/09/2026 (yêu cầu người dùng "modal Trả lời văn bản đến bị lỗi xóa tương tự"): request cho
 * {@code POST /api/v1/vbdi/XoaTraLoiVanBanDen} — port {@code TraLoiChoVanBanDen.jsx#handleDelete}
 * (danh sách hiển thị trong modal "Trả lời văn bản đến"/tab "Trả lời cho văn bản đến"), xem javadoc
 * {@link XoaTraLoiVanBanDenUseCase}.
 *
 * <p>{@code documentId}: {@code id} của phần tử cần loại khỏi {@code documentin_obj} — KIỂU STRING
 * (không phải UUID), cùng lý do {@link XoaVanBanLienQuanRequest#getDocumentId()} (phần tử có thể là
 * UUID Postgres hoặc UNID Domino).</p>
 */
@Data
public class XoaTraLoiVanBanDenRequest {

    @NotNull(message = "vbdiId khong duoc de trong")
    private UUID vbdiId;

    @NotBlank(message = "Thieu id van ban can xoa")
    private String documentId;
}
