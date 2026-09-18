package com.td.infrastructure.email;

import com.td.application.common.email.EmailAttachmentRef;
import com.td.application.common.email.EmailMessage;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-221 (VBDi — Ký số & Gửi đi) — DoD (3): {@link JavaMailEmailSender}. Dùng
 * {@code JavaMailSenderImpl} thật (không mock {@code createMimeMessage()} — tránh phải giả lập
 * {@code MimeMessage} thủ công) nhưng KHÔNG gọi {@code send()} thật (không có SMTP server trong
 * test) — chỉ xác nhận message được build đúng và cờ {@code app.email.enabled=false} chặn gửi.
 */
@ExtendWith(MockitoExtension.class)
class JavaMailEmailSenderTest {

    private final JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();

    @Mock
    private FileMetadataJpaRepository fileMetadataRepo;
    @Mock
    private MinioService minioService;

    private JavaMailEmailSender emailSender;

    @BeforeEach
    void setUp() {
        emailSender = new JavaMailEmailSender(javaMailSender, fileMetadataRepo, minioService);
        ReflectionTestUtils.setField(emailSender, "fromAddress", "qlvb@tandan.com.vn");
        ReflectionTestUtils.setField(emailSender, "sendingEnabled", true);
    }

    @Test
    void send_withSendingDisabled_shouldSkipWithoutCallingSmtp() {
        ReflectionTestUtils.setField(emailSender, "sendingEnabled", false);

        var message = EmailMessage.builder().to(List.of("a@example.com")).subject("Test").body("Noi dung").build();

        var result = emailSender.send(message);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void send_withoutRecipients_shouldReturnFailure() {
        var message = EmailMessage.builder().to(List.of()).subject("Test").body("Noi dung").build();

        var result = emailSender.send(message);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void send_withUnresolvableAttachment_shouldSkipAttachmentButStillAttemptSend() {
        UUID missingFileId = UUID.randomUUID();
        when(fileMetadataRepo.findById(missingFileId)).thenReturn(Optional.empty());

        var message = EmailMessage.builder()
            .to(List.of("a@example.com"))
            .subject("Van ban di")
            .body("Kinh gui")
            .attachments(List.of(new EmailAttachmentRef(missingFileId, "vbdi.pdf")))
            .build();

        // JavaMailSenderImpl.send() se that bai vi khong co SMTP host thuc (khong cau hinh) —
        // xac nhan Result.failure duoc tra ve gon gang (khong throw ra ngoai use case), va file
        // dinh kem khong ton tai bi bo qua an toan truoc khi toi buoc gui.
        var result = emailSender.send(message);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void send_withResolvableAttachment_shouldFetchBytesFromMinio() throws Exception {
        UUID fileId = UUID.randomUUID();
        FileMetadataEntity file = FileMetadataEntity.builder()
            .id(fileId).originalFilename("vbdi.pdf").filePath("documents/x/" + fileId + ".pdf").build();
        when(fileMetadataRepo.findById(fileId)).thenReturn(Optional.of(file));
        when(minioService.getObject(file.getFilePath())).thenReturn(new ByteArrayInputStream("pdf-bytes".getBytes()));

        var message = EmailMessage.builder()
            .to(List.of("a@example.com"))
            .subject("Van ban di")
            .body("Kinh gui")
            .attachments(List.of(new EmailAttachmentRef(fileId, "vbdi.pdf")))
            .build();

        emailSender.send(message);

        verify(minioService).getObject(file.getFilePath());
    }
}
