package com.td.web.config;

import com.td.infrastructure.security.KeycloakProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI customOpenAPI(KeycloakProperties keycloakProperties) {
    return new OpenAPI()
      .info(
        new Info()
          .title("TD Documents API")
          .description("Hệ thống quản lý văn bản - Spring Boot API")
          .version("v1.0.0")
          .contact(new Contact().name("Nhóm phát triển TD").email("dev@td.com"))
          .license(
            new License()
              .name("MIT License")
              .url("https://opensource.org/licenses/MIT")
          )
      )
      .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
      .addSecurityItem(new SecurityRequirement().addList("keycloak-oauth2"))
      .components(
        new Components()
          .addSecuritySchemes(
            "bearer-jwt",
            new SecurityScheme()
              .type(SecurityScheme.Type.HTTP)
              .scheme("bearer")
              .bearerFormat("JWT")
              .in(SecurityScheme.In.HEADER)
              .name("Authorization")
          )
          .addSecuritySchemes(
            "keycloak-oauth2",
            new SecurityScheme()
              .type(SecurityScheme.Type.OAUTH2)
              .description("Lấy token nhanh từ Keycloak ngay trong Swagger UI")
              .flows(
                new OAuthFlows()
                  .password(
                    new OAuthFlow()
                      .tokenUrl(keycloakProperties.getTokenUri())
                      .scopes(
                        new Scopes()
                          .addString("openid", "OpenID scope")
                          .addString("profile", "Thông tin hồ sơ người dùng")
                      )
                  )
                  .clientCredentials(
                    new OAuthFlow()
                      .tokenUrl(keycloakProperties.getTokenUri())
                      .scopes(
                        new Scopes()
                          .addString("openid", "OpenID scope")
                      )
                  )
              )
          )
      );
  }
}
