package com.td.worker.tasks.guinhanquocgia;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình module "Gửi nhận quốc gia" (VPCP/VXP, chuẩn eDXML QCVN 102) — bổ sung 09/09/2026.
 *
 * <p>File config MỚI, RIÊNG — CỐ Ý KHÔNG gộp vào {@code com.td.worker.config.WorkerProperties}
 * hiện có (đúng ràng buộc "chỉ thêm mới, không sửa file cấu hình đang có"). Đăng ký qua
 * {@code @Component} (Spring Boot tự nhận diện {@code @ConfigurationProperties} trên bean được
 * component-scan, không cần khai báo {@code @EnableConfigurationProperties} trong
 * {@code WorkerApplication.java}) — vì vậy không cần sửa file đó.</p>
 *
 * <p>Prefix {@code worker.tasks.guinhanquocgia} cùng convention với
 * {@code worker.tasks.vbdi}/{@code worker.tasks.vbden} đã có trong {@code WorkerProperties}. Mọi
 * giá trị nạp qua biến môi trường theo relaxed-binding chuẩn Spring Boot (vd biến môi trường
 * {@code WORKER_TASKS_GUINHANQUOCGIA_ENABLED} tự ánh xạ vào {@code enabled} — KHÔNG cần khai báo gì
 * thêm trong {@code application.yml} hiện có).</p>
 *
 * <p>Mapping tham khảo từ {@code docs/td-guinhanquocgia/resources/collaboration.properties}:</p>
 * <pre>
 *   client.protocol         -&gt; protocol        (mẫu gốc: "HTTPs")
 *   client.max-connection   -&gt; maxConnection   (mẫu gốc: 10)
 *   client.max-error-retry  -&gt; maxErrorRetry   (mẫu gốc: 4)
 *   systemid                -&gt; systemId        (do VPCP/VNPT cấp — CHƯA có giá trị thật)
 *   secret                  -&gt; secret          (do VPCP/VNPT cấp — CHƯA có giá trị thật)
 *   store-path-dir          -&gt; storePathDir    (mẫu gốc "d:/test" — KHÔNG dùng nguyên, xem dưới)
 *   service.endpoint        -&gt; endpoint        (CHƯA có giá trị thật)
 * </pre>
 *
 * <p>{@code enabled} mặc định {@code false} (an toàn) — CHƯA có endpoint/credential thật từ VPCP,
 * và các dependency transitive của SDK (gson/jdom2/httpclient4/guava/xerces) CHƯA được thêm vào
 * {@code pom.xml} ở bản scaffold này (xem lý do ở đó) — chỉ bật khi đã giải quyết đủ 2 việc này, xem
 * {@code docs/16-checklist-tich-hop-gui-nhan-quoc-gia.md} mục rủi ro.</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "worker.tasks.guinhanquocgia")
public class GuiNhanQuocGiaProperties {

    private boolean enabled = false;
    private String protocol = "HTTPs";
    private String endpoint = "";
    private String systemId = "";
    private String secret = "";
    private String storePathDir = "";
    private int maxConnection = 10;
    private int maxErrorRetry = 4;
}
