package com.td.web.controllers.v1;

import com.td.infrastructure.security.KeycloakProperties;
import com.td.web.config.GlobalExceptionHandler;
import com.td.web.controllers.SwaggerOAuthConfigController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra endpoint cấu hình OAuth cho trang Swagger tuỳ chỉnh: phải suy ra token/issuer
 * endpoint động từ {@link KeycloakProperties} (app.security.keycloak.*) thay vì hard-code
 * localhost:8180 — để "Fetch token" trên Swagger khớp với Keycloak thật đang dùng (Postman).
 */
@WebMvcTest(controllers = SwaggerOAuthConfigController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, SwaggerOAuthConfigController.class})
class SwaggerOAuthConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @SpyBean
    private KeycloakProperties keycloakProperties;

    @Test
    void oauthConfig_shouldReturnEndpointsDerivedFromKeycloakProperties() throws Exception {
        // Set field truc tiep (giong binding tu application.yml/.env) — getIssuerUri()/getTokenUri()
        // doc field truc tiep nen khong the dung when(getServerUrl()).
        keycloakProperties.setServerUrl("http://10.10.10.116:9014/keycloak");
        keycloakProperties.setRealm("td-webapi-realm");
        keycloakProperties.setClientId("td-webapi-client");
        keycloakProperties.setClientSecret("td-webapi-secret-2024");

        mockMvc.perform(get("/swagger-oauth-config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serverUrl").value("http://10.10.10.116:9014/keycloak"))
            .andExpect(jsonPath("$.issuerUri").value("http://10.10.10.116:9014/keycloak/realms/td-webapi-realm"))
            .andExpect(jsonPath("$.tokenEndpoint")
                .value("http://10.10.10.116:9014/keycloak/realms/td-webapi-realm/protocol/openid-connect/token"))
            .andExpect(jsonPath("$.clientId").value("td-webapi-client"))
            // 28/10/2026 (rà soát bảo mật): route permitAll — TUYỆT ĐỐI không trả client secret.
            .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }

    @Test
    void oauthConfig_whenClientSecretUnset_shouldStillDeriveEndpoints() throws Exception {
        keycloakProperties.setServerUrl("http://localhost:8180");
        keycloakProperties.setRealm("td-webapi-realm");
        keycloakProperties.setClientId("td-webapi-client");
        // Client secret khong set -> giu default null

        mockMvc.perform(get("/swagger-oauth-config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clientSecret").doesNotExist())
            .andExpect(jsonPath("$.tokenEndpoint")
                .value("http://localhost:8180/realms/td-webapi-realm/protocol/openid-connect/token"));
    }
}