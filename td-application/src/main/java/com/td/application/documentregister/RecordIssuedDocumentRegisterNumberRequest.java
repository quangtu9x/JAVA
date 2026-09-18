package com.td.application.documentregister;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Yêu cầu người dùng 28/08/2026 ("Chọn sổ văn bản" ở {@code ChiTietModal.jsx} vbdi, port
 * {@code updateSoVanBan}, {@code ControllModal.jsx}): ghi nhận 1 số vừa được phát cho 1 sổ văn bản
 * — dùng khi luồng tạo document (Domino cũ hoặc Java) chưa/không tự gọi
 * {@link DocumentRegister#recordIssuedNumber(int)} thay mặt caller (xem javadoc
 * {@link RecordIssuedDocumentRegisterNumberUseCase}).
 */
@Data
public class RecordIssuedDocumentRegisterNumberRequest {

    @NotNull(message = "number không được để trống")
    private Integer number;
}
