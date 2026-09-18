package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.InputStream;
import java.util.UUID;

/**
 * Yêu cầu người dùng 15/09/2026 ("port ký số cho các module khác như vbdi, vbden"): request cho
 * {@code POST /api/v1/vbden/KySo} — cùng thiết kế {@code KySoVbdiRequest} (vbdi): backend KHÔNG tự
 * ký số — người dùng đã ký số cục bộ bằng USB token (eSign/TDeSign), rồi upload file PDF ĐÃ KÝ lên
 * đây; backend chỉ lưu tệp (đánh dấu {@code isPrimary=true}, xem javadoc {@link KySoVbdenUseCase}).
 */
@Data
public class KySoVbdenRequest {

    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    private InputStream fileContent;

    private String fileName;

    private String mimeType;

    private Long fileSize;
}
