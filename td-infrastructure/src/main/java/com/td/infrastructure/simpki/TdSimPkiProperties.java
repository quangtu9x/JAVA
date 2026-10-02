package com.td.infrastructure.simpki;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình tích hợp TDSimPki ({@code C:\code_projects\HSCV\TDSimPki}, service ký PDF qua SIM-PKI) —
 * đọc từ {@code SIMPKI_*} ở {@code JAVA/.env}. Backend này là nơi DUY NHẤT giữ {@code apiKey} —
 * KHÔNG BAO GIỜ đặt secret này ở bất kỳ biến {@code VITE_*} nào phía Frontend (bị nhúng công khai
 * vào bundle JS, ai cũng đọc được qua devtools — cùng nguyên tắc {@code ONLYOFFICE_JWT_SECRET}, xem
 * {@link com.td.infrastructure.onlyoffice.OnlyOfficeProperties}).
 *
 * <p>Mặc định {@code enabled=false} (an toàn) — theo đúng tiền lệ {@code hosocv.lgsp.enabled} và
 * {@code MAIL_SENDING_ENABLED}: trả lỗi nghiệp vụ rõ ràng khi chưa cấu hình, không kết nối thất bại
 * mù mờ.</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.simpki")
public class TdSimPkiProperties {

    /** Bật/tắt tích hợp — mặc định tắt cho tới khi có base-url/api-key thật. */
    private boolean enabled = false;

    /** Base URL TDSimPki, vd {@code http://10.10.10.x:8091} — không có dấu {@code /} cuối. */
    private String baseUrl = "";

    /** {@code X-API-Key} — KHÔNG log, KHÔNG trả về Frontend. */
    private String apiKey = "";

    /**
     * {@code X-Tenant-Id} gửi cho TDSimPki — KHÁC HOÀN TOÀN tenant/database-per-tenant của chính hệ
     * thống này (xem {@code multi-tenant-database-per-tenant} trong bộ nhớ dự án); TDSimPki là 1
     * service độc lập với schema tenant riêng của nó (bảng {@code worker_pdf_files}).
     */
    private String tenantId = "default";

    private int connectTimeoutSeconds = 15;
    private int requestTimeoutSeconds = 30;
}
