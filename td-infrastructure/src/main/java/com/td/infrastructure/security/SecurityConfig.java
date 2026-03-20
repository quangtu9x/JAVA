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

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

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

        List<String> allowedOriginPatterns = new ArrayList<>(corsProperties.getAllowedOriginPatterns());
        // Keep Keycloak origin allowed by default for token helper flows in Swagger.
        if (keycloakProperties.getServerUrl() != null && !keycloakProperties.getServerUrl().isBlank()) {
            allowedOriginPatterns.add(keycloakProperties.getServerUrl());
        }

        configuration.setAllowedOriginPatterns(allowedOriginPatterns);
        configuration.setAllowedMethods(corsProperties.getAllowedMethods());
        configuration.setAllowedHeaders(corsProperties.getAllowedHeaders());
        configuration.setAllowCredentials(corsProperties.isAllowCredentials());
        configuration.setMaxAge(corsProperties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(keycloakJwtConverter);
        return converter;
    }
}