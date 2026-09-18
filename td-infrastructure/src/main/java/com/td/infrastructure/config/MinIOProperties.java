package com.td.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.minio")
public class MinIOProperties {
    private String url = "http://localhost:9000";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";
    private String bucketName = "td-webapi-files";
    private int presignedUrlExpiry = 3600;
    private Http http = new Http();

    @Data
    public static class Http {
        private int maxConnections = 100;
        private int keepAliveMinutes = 5;
        private int connectTimeoutSeconds = 10;
        private int writeTimeoutSeconds = 60;
        private int readTimeoutSeconds = 120;
    }
}
