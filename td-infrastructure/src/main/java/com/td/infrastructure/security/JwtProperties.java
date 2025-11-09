package com.td.infrastructure.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {
    private String secret = "mySecretKey";
    private long accessTokenExpiration = 3600000; // 1 hour
    private long refreshTokenExpiration = 86400000; // 24 hours
    private String issuer = "TD.WebAPI";
    private String audience = "TD.WebAPI.Users";
}