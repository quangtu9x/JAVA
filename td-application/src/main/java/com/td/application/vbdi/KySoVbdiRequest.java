package com.td.application.vbdi;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.InputStream;
import java.util.UUID;

/**
 * BL-221 (VBDi — Ký số & Gửi đi) — DoD (1): request cho {@code POST /api/v1/vbdi/KySo}. Đúng thiết
 * kế thật ({@code TDAttachment.AddSignAttachment()}): backend KHÔNG tự ký số — người dùng đã ký số
 * cục bộ bằng USB token/HSM (tích hợp bên thứ ba, ngoài phạm vi BL-221 — xem "Rủi ro"), rồi upload
 * file PDF ĐÃ KÝ lên đây; backend chỉ lưu file + đánh dấu {@code dakyso}/{@code dakycoquan}.
 */
@Data
public class KySoVbdiRequest {

    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    private InputStream fileContent;

    private String fileName;

    private String mimeType;

    private Long fileSize;

    /** {@code true} nếu đây là ký cơ quan (đúng field {@code sign_coquan}/{@code dakycoquan} thật). */
    private boolean coQuan;
}
