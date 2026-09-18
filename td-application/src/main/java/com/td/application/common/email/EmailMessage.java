package com.td.application.common.email;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * BL-221 (VBDi — Ký số & Gửi đi) — DoD (3): nội dung 1 email công vụ cần gửi (chưa có bằng chứng
 * SMTP thật từ Domino — xem Javadoc {@link EmailSender}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailMessage {

    private List<String> to;
    private String subject;
    private String body;

    @Builder.Default
    private List<EmailAttachmentRef> attachments = new ArrayList<>();
}
