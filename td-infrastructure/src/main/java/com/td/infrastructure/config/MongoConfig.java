package com.td.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.mongodb")
public class MongoConfig {
    private String host = "localhost";
    private int port = 27017;
    private String database = "tdwebapi";
    private String username;
    private String password;
    private String authenticationDatabase = "admin";
    private boolean enableAuthentication = false;
    private Connection connection = new Connection();

    @Data
    public static class Connection {
        private int maxPoolSize = 100;
        private int minPoolSize = 5;
        private int maxIdleTimeMs = 120000;
        private int maxLifeTimeMs = 300000;
        private int connectTimeoutMs = 10000;
        private int socketTimeoutMs = 0;
    }

    public String getConnectionString() {
        StringBuilder sb = new StringBuilder("mongodb://");
        
        if (enableAuthentication && username != null && password != null) {
            sb.append(username).append(":").append(password).append("@");
        }
        
        sb.append(host).append(":").append(port).append("/").append(database);
        
        if (enableAuthentication && authenticationDatabase != null) {
            sb.append("?authSource=").append(authenticationDatabase);
        }
        
        return sb.toString();
    }
}