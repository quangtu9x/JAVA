package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Yêu cầu người dùng 13/09/2026 ("đọc và port luôn nhánh 2 đi cho xong việc này" — nhánh
 * "thay thế/thu hồi" của {@code ReplyDocumentOut.java} Domino, {@code type=="vaoso"}) — request cho
 * {@code POST /api/v1/vbden/CapNhatVanBanThayTheThuHoi}, tương thích nút "Cập nhật VB"
 * ({@code capnhatVB()} — {@code chovaoso/ItemsListTuChoi.jsx}/{@code chovaoso/ItemsList.jsx}/
 * {@code denquamang/ItemsList.jsx}) — thay cho lệnh gọi Domino chết
 * {@code qlvb/vbdi?databasetype=vbden&...&action=ReplyDocumentOut&id=...&type=vaoso}.
 *
 * @see CapNhatVanBanThayTheThuHoiUseCase
 */
@Data
public class CapNhatVanBanThayTheThuHoiRequest {

    /** Id văn bản đến C — bản ghi "thay thế/thu hồi" người dùng vừa chọn ở danh sách chờ vào sổ. */
    @NotNull(message = "id không được để trống")
    private UUID id;
}
