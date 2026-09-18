package com.td.worker.tasks.simpki;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình module "Ký số SIM-PKI" (MSSP/ETSI TS 102 204, {@code docs/TDSimSign/sdk-mobilepki}).
 *
 * <p>File config MỚI, RIÊNG — CỐ Ý KHÔNG gộp vào {@code com.td.worker.config.WorkerProperties}
 * hiện có (đúng ràng buộc "chỉ thêm mới, không sửa file cấu hình đang có" — cùng quyết định đã áp
 * dụng cho {@code GuiNhanQuocGiaProperties}). Đăng ký qua {@code @Component} (Spring Boot tự nhận
 * diện {@code @ConfigurationProperties} trên bean được component-scan, không cần
 * {@code @EnableConfigurationProperties} trong {@code WorkerApplication.java}).</p>
 *
 * <p>Prefix {@code worker.tasks.simpki} cùng convention với {@code worker.tasks.vbdi}/
 * {@code worker.tasks.guinhanquocgia}. Mọi giá trị nạp qua biến môi trường theo relaxed-binding
 * chuẩn Spring Boot (vd {@code WORKER_TASKS_SIMPKI_ENABLED} tự ánh xạ vào {@code enabled} — KHÔNG
 * cần khai báo gì thêm trong {@code application.yml} hiện có).</p>
 *
 * <p><b>KHÔNG hard-code</b>: mọi endpoint/AP credential/keystore path/password ở đây là giá trị MẶC
 * ĐỊNH THAM KHẢO lấy từ SDK mẫu ({@code docs/TDSimSign/sdk-mobilepki/java/note.txt} +
 * {@code sample-code/ExtSignature.java}) — riêng {@code apId}/{@code apPassword}/
 * {@code trustStorePath}/{@code keyStorePath}/{@code keyStorePassword} PHẢI cấu hình giá trị THẬT
 * (do Cục Chứng thực số và Bảo mật thông tin cấp cho từng đơn vị/AP) qua biến môi trường trước khi
 * bật {@code enabled=true} — mặc định để rỗng, an toàn (xem {@code docs/17-checklist-tich-hop-kyso-simpki.md}).</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "worker.tasks.simpki")
public class SimPkiProperties {

    /** Công tắc bật/tắt module — mặc định TẮT (an toàn, không đụng SSL/JVM global tới khi bật). */
    private boolean enabled = false;

    /** Endpoint webservice MSSP_SignaturePort — mặc định theo note.txt SDK (link công khai của Cục CTS). */
    private String signatureUrl = "https://mpki1.ca.gov.vn:18083/soap/services/MSS_SignaturePort";

    /** MSSP_ID (URI định danh MSSP) — mặc định theo sample SDK, thường KHÔNG cần đổi. */
    private String msspId = "http://hmssp.mobile-id.vn";

    /** Signature profile URI (mssURI) — mặc định "chữ ký số" theo sample SDK. */
    private String signatureProfile = "http://mobile-id.vn/digitalSignature";

    /** MSS_Format URI — mặc định PKCS1 theo sample SDK (ETSI TS 102 204 v1.1.2). */
    private String mssFormat = "http://uri.etsi.org/TS102204/v1.1.2#PKCS1";

    /** AP_ID do Cục Chứng thực số và Bảo mật thông tin cấp cho Application Provider — CHƯA có giá trị thật. */
    private String apId = "";

    /** AP_PASSWORD tương ứng AP_ID — bí mật thật, CHƯA có giá trị mặc định. */
    private String apPassword = "";

    /** Timeout (giây) chờ người dùng xác nhận trên điện thoại — mặc định 300s theo sample SDK. */
    private int timeoutSeconds = 300;

    /** Đường dẫn truststore JKS chứa CA tin cậy của MSSP — PHẢI cấu hình trước khi bật enabled=true. */
    private String trustStorePath = "";
    private String trustStorePassword = "";

    /** Đường dẫn keystore PKCS12/JKS chứa chứng thư Client Authentication (AP) — PHẢI cấu hình thật. */
    private String keyStorePath = "";
    private String keyStorePassword = "";
    /** "PKCS12" hoặc "JKS" — mặc định PKCS12 theo mẫu SDK ({@code samplemssp}). */
    private String keyStoreType = "PKCS12";

    /**
     * Nội dung hiển thị trên điện thoại người dùng khi xác nhận giao dịch ký — PHẢI chứa
     * {@link SimPkiTaskConstants#VC_PLACEHOLDER} ({@code %%VC%%}, ứng dụng trên SIM tự thay bằng mã
     * xác thực, KHÔNG phải server thay) nếu muốn hiển thị mã xác thực; payload từng lần ký có thể tự
     * override field {@code displayMessage} (xem {@link SimPkiSignPayload}), giá trị này chỉ là mặc
     * định khi payload không truyền.
     */
    private String displayMessageTemplate = "Ma giao dich " + SimPkiTaskConstants.VC_PLACEHOLDER
        + ". Vui long xac nhan tren dien thoai de ky so van ban.";

    /** taskType envelope phản hồi kết quả ký, publish qua RabbitMQ khi {@code worker.rabbit.enabled=true}. */
    private String replyTaskType = SimPkiTaskConstants.TASK_SIGN_REPLY_DEFAULT;
}
