package com.td.infrastructure.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.security.cors")
public class CorsProperties {

    private List<String> allowedOriginPatterns = new ArrayList<>(List.of(
        "http://localhost:*",
        "https://localhost:*",
        "http://127.0.0.1:*",
        "https://127.0.0.1:*",
        "http://[::1]:*",
        "https://[::1]:*"
    ));

    private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

    private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

    private boolean allowCredentials = true;

    private long maxAge = 3600L;
}