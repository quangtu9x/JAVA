package com.td.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.td.infrastructure.tenant.TenantFilter;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private static final List<String> REQUIRED_PUBLIC_ORIGIN_PATTERNS = List.of(
        "http://hp.tandan.com.vn",
        "https://hp.tandan.com.vn",
        "https://qlvbjava.tandan.com.vn",
        "https://*.tandan.com.vn"
    );

    private final KeycloakJwtConverter keycloakJwtConverter;
    private final KeycloakProperties keycloakProperties;
    private final CorsProperties corsProperties;
    private final TdTokenAuthenticationFilter tdTokenAuthenticationFilter;
    private final ApiResponseAuthenticationEntryPoint apiResponseAuthenticationEntryPoint;
    private final ApiResponseAccessDeniedHandler apiResponseAccessDeniedHandler;
    private final TenantFilter tenantFilter;
    private final AccountProvisioningFilter accountProvisioningFilter;

    /**
     * Chain RIÊNG, thứ tự cao hơn (chạy trước) cho đúng 2 route Document Server OnlyOffice gọi
     * thẳng (thử nghiệm — xem {@code OnlyOfficeController}). BẮT BUỘC tách chain thay vì chỉ thêm
     * {@code permitAll()} vào chain chính bên dưới: {@code permitAll()} chỉ bỏ qua bước ỦY QUYỀN,
     * nhưng {@code BearerTokenAuthenticationFilter} (Keycloak) trong chain chính vẫn chạy TRƯỚC bước
     * đó — nếu Document Server gửi kèm header {@code Authorization: Bearer <jwt do chinh no ky>}
     * (JWT OnlyOffice, KHÔNG phải JWT Keycloak — xem {@code app.onlyoffice.jwt-header}, mặc định
     * cũng tên "Authorization"), filter đó sẽ cố verify như JWT Keycloak, thất bại, và trả 401 NGAY,
     * request không bao giờ tới được Controller dù route đã {@code permitAll()}. Dùng
     * {@code securityMatcher(...)} tách hẳn 2 route này sang 1 {@link SecurityFilterChain} không có
     * {@code oauth2ResourceServer}/Tdtoken/{@link TenantFilter} — matcher không khớp thì Spring tự
     * chuyển sang chain còn lại (order thấp hơn) như cũ, KHÔNG ảnh hưởng route nào khác.
     */
    @Bean
    @org.springframework.core.annotation.Order(1)
    public SecurityFilterChain onlyOfficeExternalFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v1/onlyoffice/content", "/api/v1/onlyoffice/callback")
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @org.springframework.core.annotation.Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptionHandling -> exceptionHandling
                .authenticationEntryPoint(apiResponseAuthenticationEntryPoint)
                .accessDeniedHandler(apiResponseAccessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/api/health/**").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/swagger-native.html", "/swagger-custom/**", "/v3/api-docs/**", "/v3/api-docs").permitAll()
                // Cau hinh OAuth cho trang Swagger tu tuy chinh (khong nam trong thu muc static).
                .requestMatchers("/swagger-oauth-config").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Protected endpoints with role-based access
                .requestMatchers(HttpMethod.GET, "/api/v1/documents/**").hasAnyRole("USER", "ADMIN", "PRODUCT_MANAGER", "BRAND_MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/v1/documents/search").hasAnyRole("USER", "ADMIN", "PRODUCT_MANAGER", "BRAND_MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/v1/documents").hasAnyRole("USER", "ADMIN", "PRODUCT_MANAGER", "BRAND_MANAGER")
                .requestMatchers(HttpMethod.PUT, "/api/v1/documents/**").hasAnyRole("USER", "ADMIN", "PRODUCT_MANAGER", "BRAND_MANAGER")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/documents/**").hasAnyRole("USER", "ADMIN", "PRODUCT_MANAGER", "BRAND_MANAGER")
                
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .authenticationEntryPoint(apiResponseAuthenticationEntryPoint)
                .jwt(jwt -> jwt
                    .jwkSetUri(keycloakProperties.getJwkSetUri())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )
            // Tdtoken (BL-004): xử lý truoc BearerTokenAuthenticationFilter, chỉ can thiệp khi có
            // header Tdtoken và không có Authorization — nếu không sẽ nhường luồng Keycloak.
            .addFilterBefore(tdTokenAuthenticationFilter, BearerTokenAuthenticationFilter.class)
            // "Khóa triệt để" (22/09/2026, xem javadoc AccountProvisioningFilter): chặn MỌI request
            // đã xác thực (Keycloak hoặc Tdtoken) nhưng tài khoản chưa có AppUser — đặt SAU cả 2
            // filter xác thực (Authentication đã sẵn sàng) NHƯNG TRƯỚC TenantFilter (không được để
            // tài khoản chưa khai báo lọt tới bước resolve tenant).
            .addFilterAfter(accountProvisioningFilter, BearerTokenAuthenticationFilter.class)
            // Tenant routing (BL-303): chạy SAU accountProvisioningFilter — Authentication đã sẵn
            // sàng VÀ đã qua được cửa provisioning để suy ra tenant_id trước khi vào Controller.
            .addFilterAfter(tenantFilter, AccountProvisioningFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        Set<String> allowedOriginPatterns = new LinkedHashSet<>();
        for (String originPattern : corsProperties.getAllowedOriginPatterns()) {
            String normalizedOriginPattern = normalizeOriginPattern(originPattern);
            if (!normalizedOriginPattern.isBlank()) {
                allowedOriginPatterns.add(normalizedOriginPattern);
            }
        }

        // Always keep required public domains allowed even when env vars override defaults.
        for (String requiredOriginPattern : REQUIRED_PUBLIC_ORIGIN_PATTERNS) {
            String normalizedRequiredOriginPattern = normalizeOriginPattern(requiredOriginPattern);
            if (!normalizedRequiredOriginPattern.isBlank()) {
                allowedOriginPatterns.add(normalizedRequiredOriginPattern);
            }
        }

        // Keep Keycloak origin allowed by default for token helper flows in Swagger.
        if (keycloakProperties.getServerUrl() != null && !keycloakProperties.getServerUrl().isBlank()) {
            String keycloakOrigin = normalizeOriginPattern(keycloakProperties.getServerUrl());
            if (!keycloakOrigin.isBlank()) {
                allowedOriginPatterns.add(keycloakOrigin);
            }
        }

        configuration.setAllowedOriginPatterns(new ArrayList<>(allowedOriginPatterns));
        configuration.setAllowedMethods(corsProperties.getAllowedMethods());
        configuration.setAllowedHeaders(corsProperties.getAllowedHeaders());
        configuration.setAllowCredentials(corsProperties.isAllowCredentials());
        configuration.setMaxAge(corsProperties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private String normalizeOriginPattern(String originPattern) {
        if (originPattern == null) {
            return "";
        }

        String normalized = originPattern.trim();
        if (normalized.isEmpty()) {
            return "";
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        // Keep wildcard patterns as-is (for example: https://*.example.com).
        if (normalized.contains("*")) {
            return normalized;
        }

        try {
            URI uri = URI.create(normalized);
            if (uri.getScheme() != null && uri.getHost() != null) {
                StringBuilder origin = new StringBuilder(uri.getScheme())
                    .append("://")
                    .append(uri.getHost());
                if (uri.getPort() != -1) {
                    origin.append(":").append(uri.getPort());
                }
                return origin.toString();
            }
        } catch (IllegalArgumentException ignored) {
            // Fall back to original normalized value if not a strict URI.
        }

        return normalized;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(keycloakJwtConverter);
        return converter;
    }
}