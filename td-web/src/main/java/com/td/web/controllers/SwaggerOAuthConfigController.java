package com.td.web.controllers;

import com.td.infrastructure.security.KeycloakProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cung cap cau hinh Keycloak/OAuth2 cho cac trang Swagger tu tuy chinh
 * ({@code swagger-custom/index.html}, {@code swagger-ui/index.html}).
 *
 * Muc dich: truoc day cac trang nay hard-code token endpoint {@code localhost:8180},
 * khong khop voi Keycloak that dang dung (vi du .env cau hinh 10.10.10.116) nen "Fetch
 * token" tren Swagger loi trong khi Postman van chay duoc. Endpoint nay tra ve gia tri
 * dong lay tu {@code app.security.keycloak.*} de trang Swagger tu nap route/client cho
 * dung, khong can hard-code.
 */
@RestController
@RequiredArgsConstructor
public class SwaggerOAuthConfigController {

    private final KeycloakProperties keycloakProperties;

    @GetMapping("/swagger-oauth-config")
    public Map<String, String> oauthConfig() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("serverUrl", keycloakProperties.getServerUrl());
        config.put("realm", keycloakProperties.getRealm());
        config.put("issuerUri", keycloakProperties.getIssuerUri());
        config.put("tokenEndpoint", keycloakProperties.getTokenUri());
        config.put("authorizationEndpoint", keycloakProperties.getAuthorizationUri());
        config.put("clientId", keycloakProperties.getClientId());
        // 28/10/2026 (rà soát bảo mật): KHÔNG trả clientSecret — route này permitAll, trước đây ai cũng
        // đọc được secret của confidential client rồi gọi thẳng Keycloak (password grant/client_credentials).
        // Người dùng Swagger tự nhập secret vào ô "Client Secret" khi cần.
        return config;
    }
}