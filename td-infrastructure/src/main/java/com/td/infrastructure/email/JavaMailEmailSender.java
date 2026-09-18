package com.td.infrastructure.email;

import com.td.application.common.email.EmailAttachmentRef;
import com.td.application.common.email.EmailMessage;
import com.td.application.common.email.EmailSender;
import com.td.application.common.models.Result;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * BL-221 (VBDi — Ký số & Gửi đi) — DoD (3): impl thật của {@link EmailSender} qua SMTP (Spring
 * {@code JavaMailSender}, cấu hình {@code spring.mail.*}). Xem Javadoc {@link EmailSender} về giới
 * hạn bằng chứng nguồn (không có SMTP thật trong Domino — thiết kế mới cho Spring Boot).
 *
 * <p>{@code app.email.enabled=false} tắt hẳn việc gửi thật (mặc định {@code true}) — dùng khi
 * chưa cấu hình SMTP relay thật (staging/local dev) để tránh lỗi kết nối làm hỏng luồng nghiệp vụ
 * chính; log cảnh báo thay vì gửi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JavaMailEmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;
    private final FileMetadataJpaRepository fileMetadataRepo;
    private final MinioService minioService;

    @Value("${app.email.from:qlvb@tandan.com.vn}")
    private String fromAddress;

    @Value("${app.email.enabled:true}")
    private boolean sendingEnabled;

    @Override
    public Result<Void> send(EmailMessage message) {
        if (!sendingEnabled) {
            log.info("Gui email bi tat (app.email.enabled=false) - bo qua gui that cho: {}", message.getTo());
            return Result.success(null);
        }
        if (message.getTo() == null || message.getTo().isEmpty()) {
            return Result.failure("Danh sách người nhận không được để trống");
        }

        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(message.getTo().toArray(new String[0]));
            helper.setSubject(message.getSubject() != null ? message.getSubject() : "");
            helper.setText(message.getBody() != null ? message.getBody() : "", false);

            if (message.getAttachments() != null) {
                for (EmailAttachmentRef attachment : message.getAttachments()) {
                    attachFile(helper, attachment);
                }
            }

            javaMailSender.send(mimeMessage);
            return Result.success(null);
        } catch (Exception ex) {
            log.warn("Gui email that bai toi {}: {}", message.getTo(), ex.getMessage());
            return Result.failure("Gửi email thất bại: " + ex.getMessage());
        }
    }

    private void attachFile(MimeMessageHelper helper, EmailAttachmentRef attachment) throws Exception {
        var fileOptional = fileMetadataRepo.findById(attachment.getFileId());
        if (fileOptional.isEmpty()) {
            log.warn("Bo qua dinh kem - khong tim thay file {}", attachment.getFileId());
            return;
        }
        FileMetadataEntity file = fileOptional.get();
        try (var inputStream = minioService.getObject(file.getFilePath())) {
            byte[] content = inputStream.readAllBytes();
            String fileName = attachment.getFileName() != null ? attachment.getFileName() : file.getOriginalFilename();
            helper.addAttachment(fileName, new org.springframework.core.io.ByteArrayResource(content));
        }
    }
}
