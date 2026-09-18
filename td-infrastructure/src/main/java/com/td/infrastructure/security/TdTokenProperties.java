package com.td.infrastructure.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình cho Authentication Bridge (BL-004): cho phép xác thực bằng header {@code Tdtoken}
 * (JWT cũ ký HMAC bằng secret của Domino {@code TDKeySettings.JWT_Authen_Secret_KEY}) song song
 * với Keycloak OAuth2. Secret phải được copy sang biến môi trường {@code TDTOKEN_JWT_SECRET},
 * không hard-code trong source.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.security.tdtoken")
public class TdTokenProperties {

    /**
     * Bật/tắt toàn bộ cơ chế xác thực Tdtoken. Tắt khi FE đã chuyển hẳn sang Keycloak.
     */
    private boolean enabled = true;

    /**
     * Secret HMAC dùng để verify JWT cũ — phải trùng với secret Domino đang dùng để ký token.
     */
    private String secret;

    /**
     * Dung sai lệch giờ giữa server ký token (Domino) và server verify (Spring Boot), tính bằng giây.
     */
    private long clockSkewSeconds = 60;
}
