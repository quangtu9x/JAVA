package com.td.infrastructure.storage;

import com.td.domain.storage.FileCategory;
import com.td.domain.storage.FileMetadata;
import com.td.infrastructure.config.MinIOProperties;
import io.minio.*;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinIOService {

    private final MinioClient minioClient;
    private final MinIOProperties minIOProperties;

    /**
     * Upload file to MinIO and return FileMetadata
     */
    public FileMetadata uploadFile(MultipartFile file, FileCategory category, UUID uploadedBy, String description) {
        try {
            // Validate file
            validateFile(file);
            
            // Ensure bucket exists
            ensureBucketExists();
            
            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            String storedFilename = generateUniqueFilename(originalFilename);
            String filePath = category.getFullPath() + storedFilename;
            
            // Upload to MinIO
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(minIOProperties.getBucketName())
                    .object(filePath)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build();
                    
            ObjectWriteResponse response = minioClient.putObject(args);
            
            // Create FileMetadata
            FileMetadata metadata = new FileMetadata();
            metadata.setId(UUID.randomUUID());
            metadata.setOriginalFilename(originalFilename);
            metadata.setStoredFilename(storedFilename);
            metadata.setFilePath(filePath);
            metadata.setFileSize(file.getSize());
            metadata.setContentType(file.getContentType());
            metadata.setFileExtension(fileExtension);
            metadata.setFileCategory(category);
            metadata.setBucketName(minIOProperties.getBucketName());
            metadata.setUploadedBy(uploadedBy);
            metadata.setUploadedAt(Instant.now());
            metadata.setDescription(description);
            metadata.setIsPublic(false);
            
            log.info("File uploaded successfully: {} -> {}", originalFilename, filePath);
            return metadata;
            
        } catch (Exception e) {
            log.error("Failed to upload file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
        }
    }

    /**
     * Download file from MinIO
     */
    public InputStream downloadFile(String filePath) {
        try {
            GetObjectArgs args = GetObjectArgs.builder()
                    .bucket(minIOProperties.getBucketName())
                    .object(filePath)
                    .build();
                    
            GetObjectResponse response = minioClient.getObject(args);
            log.info("File downloaded successfully: {}", filePath);
            return response;
            
        } catch (Exception e) {
            log.error("Failed to download file: {}", filePath, e);
            throw new RuntimeException("Failed to download file: " + e.getMessage(), e);
        }
    }

    /**
     * Delete file from MinIO
     */
    public void deleteFile(String filePath) {
        try {
            RemoveObjectArgs args = RemoveObjectArgs.builder()
                    .bucket(minIOProperties.getBucketName())
                    .object(filePath)
                    .build();
                    
            minioClient.removeObject(args);
            log.info("File deleted successfully: {}", filePath);
            
        } catch (Exception e) {
            log.error("Failed to delete file: {}", filePath, e);
            throw new RuntimeException("Failed to delete file: " + e.getMessage(), e);
        }
    }

    /**
     * Generate presigned URL for file download
     */
    public String generatePresignedDownloadUrl(String filePath, int expiryMinutes) {
        try {
            GetPresignedObjectUrlArgs args = GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(minIOProperties.getBucketName())
                    .object(filePath)
                    .expiry(expiryMinutes, TimeUnit.MINUTES)
                    .build();
                    
            String url = minioClient.getPresignedObjectUrl(args);
            log.debug("Generated presigned URL for file: {}", filePath);
            return url;
            
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for file: {}", filePath, e);
            throw new RuntimeException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }

    /**
     * Generate presigned URL for file upload
     */
    public String generatePresignedUploadUrl(String filePath, int expiryMinutes) {
        try {
            GetPresignedObjectUrlArgs args = GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(minIOProperties.getBucketName())
                    .object(filePath)
                    .expiry(expiryMinutes, TimeUnit.MINUTES)
                    .build();
                    
            String url = minioClient.getPresignedObjectUrl(args);
            log.debug("Generated presigned upload URL for file: {}", filePath);
            return url;
            
        } catch (Exception e) {
            log.error("Failed to generate presigned upload URL for file: {}", filePath, e);
            throw new RuntimeException("Failed to generate presigned upload URL: " + e.getMessage(), e);
        }
    }

    /**
     * Check if file exists in MinIO
     */
    public boolean fileExists(String filePath) {
        try {
            StatObjectArgs args = StatObjectArgs.builder()
                    .bucket(minIOProperties.getBucketName())
                    .object(filePath)
                    .build();
                    
            minioClient.statObject(args);
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get file information from MinIO
     */
    public ObjectStat getFileInfo(String filePath) {
        try {
            StatObjectArgs args = StatObjectArgs.builder()
                    .bucket(minIOProperties.getBucketName())
                    .object(filePath)
                    .build();
                    
            return minioClient.statObject(args);
            
        } catch (Exception e) {
            log.error("Failed to get file info: {}", filePath, e);
            throw new RuntimeException("Failed to get file info: " + e.getMessage(), e);
        }
    }

    /**
     * List files in a directory
     */
    public List<Result<Item>> listFiles(String prefix) {
        try {
            ListObjectsArgs args = ListObjectsArgs.builder()
                    .bucket(minIOProperties.getBucketName())
                    .prefix(prefix)
                    .recursive(false)
                    .build();
                    
            Iterable<Result<Item>> results = minioClient.listObjects(args);
            return (List<Result<Item>>) results;
            
        } catch (Exception e) {
            log.error("Failed to list files with prefix: {}", prefix, e);
            throw new RuntimeException("Failed to list files: " + e.getMessage(), e);
        }
    }

    /**
     * Copy file within MinIO
     */
    public void copyFile(String sourceFilePath, String destinationFilePath) {
        try {
            CopyObjectArgs args = CopyObjectArgs.builder()
                    .bucket(minIOProperties.getBucketName())
                    .object(destinationFilePath)
                    .source(CopySource.builder()
                            .bucket(minIOProperties.getBucketName())
                            .object(sourceFilePath)
                            .build())
                    .build();
                    
            minioClient.copyObject(args);
            log.info("File copied successfully: {} -> {}", sourceFilePath, destinationFilePath);
            
        } catch (Exception e) {
            log.error("Failed to copy file: {} -> {}", sourceFilePath, destinationFilePath, e);
            throw new RuntimeException("Failed to copy file: " + e.getMessage(), e);
        }
    }

    // Private helper methods
    
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be null or empty");
        }
        
        if (!minIOProperties.isAllowedFileSize(file.getSize())) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size: " + 
                minIOProperties.getMaxFileSize() + " bytes");
        }
        
        String extension = getFileExtension(file.getOriginalFilename());
        if (!minIOProperties.isAllowedExtension(extension)) {
            throw new IllegalArgumentException("File extension '" + extension + 
                "' is not allowed. Allowed extensions: " + minIOProperties.getAllowedExtensions());
        }
    }
    
    private void ensureBucketExists() {
        try {
            BucketExistsArgs existsArgs = BucketExistsArgs.builder()
                    .bucket(minIOProperties.getBucketName())
                    .build();
                    
            if (!minioClient.bucketExists(existsArgs)) {
                MakeBucketArgs makeArgs = MakeBucketArgs.builder()
                        .bucket(minIOProperties.getBucketName())
                        .build();
                        
                minioClient.makeBucket(makeArgs);
                log.info("Created MinIO bucket: {}", minIOProperties.getBucketName());
            }
        } catch (Exception e) {
            log.error("Failed to ensure bucket exists: {}", minIOProperties.getBucketName(), e);
            throw new RuntimeException("Failed to ensure bucket exists: " + e.getMessage(), e);
        }
    }
    
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
    
    private String generateUniqueFilename(String originalFilename) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String extension = getFileExtension(originalFilename);
        
        String baseName = originalFilename;
        if (baseName.contains(".")) {
            baseName = baseName.substring(0, baseName.lastIndexOf("."));
        }
        
        // Clean filename for safety
        baseName = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        return String.format("%s_%s_%s%s", baseName, timestamp, uuid, 
            extension.isEmpty() ? "" : "." + extension);
    }
}