package com.td.infrastructure.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.security.keycloak")
public class KeycloakProperties {
    
    /**
     * Keycloak server URL (e.g., http://localhost:8180)
     */
    private String serverUrl = "http://localhost:8180";
    
    /**
     * Keycloak realm name
     */
    private String realm = "td-webapi-realm";
    
    /**
     * OAuth2 client ID
     */
    private String clientId = "td-webapi-client";
    
    /**
     * OAuth2 client secret
     */
    private String clientSecret;
    
    /**
     * JWT issuer URL (auto-constructed from server URL and realm)
     */
    public String getIssuerUri() {
        return serverUrl + "/realms/" + realm;
    }
    
    /**
     * JWK Set URI for token validation (auto-constructed)
     */
    public String getJwkSetUri() {
        return getIssuerUri() + "/protocol/openid-connect/certs";
    }
    
    /**
     * Token endpoint for programmatic token requests
     */
    public String getTokenUri() {
        return getIssuerUri() + "/protocol/openid-connect/token";
    }
    
    /**
     * Authorization endpoint for OAuth2 authorization code flow
     */
    public String getAuthorizationUri() {
        return getIssuerUri() + "/protocol/openid-connect/auth";
    }
    
    /**
     * User info endpoint for retrieving user details
     */
    public String getUserInfoUri() {
        return getIssuerUri() + "/protocol/openid-connect/userinfo";
    }
    
    /**
     * Logout endpoint
     */
    public String getLogoutUri() {
        return getIssuerUri() + "/protocol/openid-connect/logout";
    }
    
    /**
     * Well-known configuration endpoint
     */
    public String getWellKnownUri() {
        return getIssuerUri() + "/.well-known/openid_configuration";
    }
}