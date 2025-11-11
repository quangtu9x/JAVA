package com.td.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.minio")
public class MinIOProperties {
    
    /**
     * MinIO server URL (e.g., http://localhost:9000)
     */
    private String url = "http://localhost:9000";
    
    /**
     * MinIO access key (username)
     */
    private String accessKey = "minioadmin";
    
    /**
     * MinIO secret key (password)
     */
    private String secretKey = "minioadmin";
    
    /**
     * Default bucket name for storing files
     */
    private String bucketName = "td-webapi-files";
    
    /**
     * Connection timeout in seconds
     */
    private int connectTimeout = 30;
    
    /**
     * Read timeout in seconds
     */
    private int readTimeout = 60;
    
    /**
     * Write timeout in seconds
     */
    private int writeTimeout = 60;
    
    /**
     * Maximum file size allowed (in bytes). Default: 10MB
     */
    private long maxFileSize = 10 * 1024 * 1024; // 10MB
    
    /**
     * Allowed file extensions (comma-separated)
     */
    private String allowedExtensions = "jpg,jpeg,png,gif,pdf,doc,docx,xls,xlsx,txt,zip";
    
    /**
     * Directory structure for organizing files
     */
    private Directories directories = new Directories();
    
    @Data
    public static class Directories {
        private String products = "products";
        private String brands = "brands";
        private String users = "users";
        private String documents = "documents";
        private String temp = "temp";
        
        /**
         * Get full path for a directory
         */
        public String getPath(String directory) {
            return directory + "/";
        }
    }
    
    /**
     * Get the full MinIO server URL
     */
    public String getServerUrl() {
        return url;
    }
    
    /**
     * Check if file extension is allowed
     */
    public boolean isAllowedExtension(String extension) {
        if (extension == null || extension.trim().isEmpty()) {
            return false;
        }
        String cleanExtension = extension.toLowerCase().replaceFirst("^\\.", "");
        return allowedExtensions.toLowerCase().contains(cleanExtension);
    }
    
    /**
     * Check if file size is within limits
     */
    public boolean isAllowedFileSize(long size) {
        return size > 0 && size <= maxFileSize;
    }
}