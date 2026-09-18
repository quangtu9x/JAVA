package com.td.infrastructure.onlyoffice;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình tích hợp OnlyOffice Document Server — đọc từ {@code ONLYOFFICE_*} ở {@code JAVA/.env}
 * (xem {@code JAVA/docs/ONLYOFFICE_FE_INTEGRATION.md}). Đây là code Java ĐẦU TIÊN đọc các biến này
 * — trước đó chỉ có FE (component {@code OnlyOfficeEditor}) trỏ tới {@code docserverUrl}.
 *
 * <p><b>2 secret khác nhau, không dùng lẫn</b>:</p>
 * <ul>
 *   <li>{@code jwtSecret} ({@code ONLYOFFICE_JWT_SECRET}) — secret CHIA SẺ với chính Document
 *       Server, dùng để ký field {@code config.token} (Document Server verify khi mở tài liệu) và
 *       verify JWT Document Server đính kèm khi gọi ngược {@code callbackUrl}.</li>
 *   <li>{@code internalTokenSecret} ({@code ONLYOFFICE_INTERNAL_TOKEN_SECRET}) — secret RIÊNG của
 *       Backend, KHÔNG chia sẻ với Document Server, dùng để tự ký token ngắn hạn nhúng trong
 *       {@code document.url}/{@code callbackUrl} (xem {@code OnlyOfficeAccessTokenService}) — 2
 *       endpoint này Document Server gọi thẳng, không gửi kèm {@code Authorization}/{@code Tdtoken}
 *       của người dùng thật nên cần 1 cơ chế xác thực phạm vi hẹp riêng, tách biệt hoàn toàn khỏi
 *       secret của Document Server để lộ 1 bên không ảnh hưởng bên còn lại.</li>
 * </ul>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.onlyoffice")
public class OnlyOfficeProperties {

    /** Địa chỉ Document Server, vd {@code http://10.10.10.107:8081} — không có dấu {@code /} cuối. */
    private String docserverUrl = "";

    /** Document Server hiện có bật JWT hay không (phải khớp đúng cấu hình thật trên Document Server). */
    private boolean jwtEnabled = false;

    /** Secret HS256 chia sẻ với Document Server — KHÔNG log ra, KHÔNG trả về FE. */
    private String jwtSecret = "";

    /** Tên header Document Server gửi kèm JWT khi gọi callback (mặc định "Authorization"). */
    private String jwtHeader = "Authorization";

    /** Document Server có gửi kèm JWT trong body callback (field {@code token}) hay không. */
    private boolean jwtInBody = true;

    /**
     * Secret HS256 RIÊNG của Backend (không chia sẻ Document Server) — ký token phạm vi hẹp cho
     * {@code document.url}/{@code callbackUrl}. Xem javadoc lớp.
     */
    private String internalTokenSecret = "";

    /**
     * Base URL Document Server dùng để gọi ngược vào Backend Java (vd {@code http://10.10.10.x:8111})
     * — PHẢI là địa chỉ mạng Document Server thật sự truy cập được, KHÔNG phải {@code localhost} của
     * máy dev hay domain public riêng của FE. Để trống → tự suy ra từ request hiện tại (best-effort,
     * chỉ dùng được nếu Backend và Document Server cùng truy cập được qua cùng 1 host/IP).
     */
    private String callbackBaseUrl = "";

    /** Thời hạn (phút) của token cấp cho {@code document.url} (tải nội dung tệp). */
    private long contentTokenTtlMinutes = 240;

    /** Thời hạn (phút) của token cấp cho {@code callbackUrl} (lưu tệp đã sửa). */
    private long callbackTokenTtlMinutes = 240;
}
