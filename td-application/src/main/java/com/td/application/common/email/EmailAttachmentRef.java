package com.td.application.common.email;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * BL-221 (VBDi — Ký số & Gửi đi) — DoD (3): tham chiếu tới 1 file đã lưu (qua
 * {@link com.td.application.documents.UploadFileUseCase}) để đính kèm khi gửi email — chỉ mang
 * {@code fileId}, KHÔNG mang byte nội dung (tránh {@code td-application} phụ thuộc ngược vào
 * {@code MinioService} ở {@code td-infrastructure}) — impl thật của {@link EmailSender} tự tra
 * cứu + tải byte file khi gửi.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailAttachmentRef {

    private UUID fileId;
    private String fileName;
}
