package com.td.infrastructure.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinIOConfig {

    private final MinIOProperties minIOProperties;

    /**
     * Creates MinIO client bean with configured settings
     */
    @Bean
    public MinioClient minioClient() {
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(minIOProperties.getUrl())
                    .credentials(minIOProperties.getAccessKey(), minIOProperties.getSecretKey())
                    .build();
            
            // Set timeouts
            client.setTimeout(
                    Duration.ofSeconds(minIOProperties.getConnectTimeout()),
                    Duration.ofSeconds(minIOProperties.getWriteTimeout()),
                    Duration.ofSeconds(minIOProperties.getReadTimeout())
            );
            
            log.info("MinIO client initialized successfully with endpoint: {}", minIOProperties.getUrl());
            return client;
            
        } catch (Exception e) {
            log.error("Failed to initialize MinIO client", e);
            throw new RuntimeException("Failed to initialize MinIO client", e);
        }
    }
}