package com.td.application.common.email;

import com.td.application.common.models.Result;

/**
 * BL-221 (VBDi — Ký số & Gửi đi) — DoD (3): cổng (port) gửi email công vụ.
 *
 * <p><b>Ghi chú nguồn gốc (khác biệt so với Domino thật)</b>: rà soát toàn bộ source Domino
 * (`grep` cho {@code javax.mail}/{@code MimeMessage}/{@code SmtpTransport}) KHÔNG tìm thấy bất kỳ
 * tích hợp SMTP/email thật nào — cơ chế gần nhất là "mail-in database" ({@code TDInitializeDatabase
 * .CreateMailin()}), một khái niệm định tuyến document nội bộ GIỮA CÁC DATABASE NSF trong cùng hạ
 * tầng Domino, khác bản chất hoàn toàn với "gửi email tới hộp thư công vụ" như tên gọi DoD (3) mô
 * tả. Vì không có bằng chứng nghiệp vụ cụ thể (không có field người nhận email, không có điều kiện
 * kích hoạt cụ thể nào được tìm thấy), impl ở đây là thiết kế tối giản, tổng quát (danh sách email
 * người nhận truyền trực tiếp bởi caller) chứ không suy diễn quy tắc nghiệp vụ nào thêm — xem
 * `docs/migration-backlog/04-migration-status.md` mục BL-221 để biết chi tiết gap này. Impl thật
 * (Spring {@code JavaMailSender}) nằm ở {@code td-infrastructure}, cần cấu hình SMTP relay thật
 * ({@code spring.mail.*}) trước khi dùng cho nghiệp vụ thật.
 */
public interface EmailSender {

    Result<Void> send(EmailMessage message);
}
