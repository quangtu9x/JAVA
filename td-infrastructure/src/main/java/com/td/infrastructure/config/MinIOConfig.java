package com.td.infrastructure.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(MinIOProperties.class)
public class MinIOConfig {

    private final MinIOProperties minioProperties;

    @Bean(name = "minioOkHttpClient")
    public OkHttpClient minioOkHttpClient() {
        MinIOProperties.Http http = minioProperties.getHttp();
        return new OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(
                        http.getMaxConnections(),
                        http.getKeepAliveMinutes(),
                        TimeUnit.MINUTES))
                .connectTimeout(http.getConnectTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(http.getWriteTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(http.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
    }

    @Bean
    public MinioClient minioClient(OkHttpClient minioOkHttpClient) {
        return MinioClient.builder()
                .endpoint(minioProperties.getUrl())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .httpClient(minioOkHttpClient)
                .build();
    }
}
