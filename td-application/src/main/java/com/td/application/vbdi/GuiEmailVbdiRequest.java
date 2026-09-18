package com.td.application.vbdi;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BL-221 (VBDi — Ký số & Gửi đi) — DoD (3): request cho {@code POST /api/v1/vbdi/GuiEmail}. Không
 * có bằng chứng field/nghiệp vụ cụ thể từ Domino thật (xem Javadoc
 * {@link com.td.application.common.email.EmailSender}) nên thiết kế tối giản: người gọi tự truyền
 * danh sách email người nhận, không suy diễn quy tắc "người nhận mặc định".
 */
@Data
public class GuiEmailVbdiRequest {

    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    @NotEmpty(message = "toEmails khong duoc de trong")
    private List<String> toEmails = new ArrayList<>();

    private String subject;

    private String message;
}
