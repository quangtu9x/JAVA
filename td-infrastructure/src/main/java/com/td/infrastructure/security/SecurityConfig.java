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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/api/health/**").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/swagger-native.html", "/swagger-custom/**", "/v3/api-docs/**", "/v3/api-docs").permitAll()
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
                .jwt(jwt -> jwt
                    .jwkSetUri(keycloakProperties.getJwkSetUri())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

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