package com.td.infrastructure.config;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;
    private final MinIOProperties minioProperties;

    public void ensureBucketExists() {
        try {
            String bucket = minioProperties.getBucketName();
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("Could not ensure MinIO bucket exists: {}", e.getMessage());
        }
    }

    public void uploadObject(String objectName, InputStream inputStream, long size, String contentType) {
        try {
            ensureBucketExists();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .stream(inputStream, size, -1)
                            .contentType(contentType != null ? contentType : "application/octet-stream")
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to MinIO: " + e.getMessage(), e);
        }
    }

    // 27/08/2026 (port "Chuyển văn thư" — DraftToDocumentOut.CopyDraftToDocumentOut() copy tệp
    // duthao_file -> vbdi_file thật): copy server-side qua MinIO (source/dest CÙNG bucket, khác
    // object path) — không tải byte về rồi tải lên lại như copy thủ công qua InputStream.
    public void copyObject(String sourceObjectName, String destObjectName) {
        try {
            ensureBucketExists();
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(destObjectName)
                            .source(CopySource.builder()
                                    .bucket(minioProperties.getBucketName())
                                    .object(sourceObjectName)
                                    .build())
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to copy file in MinIO: " + e.getMessage(), e);
        }
    }

    public InputStream getObject(String objectName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to get file from MinIO: " + e.getMessage(), e);
        }
    }

    public void removeObject(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.warn("Failed to remove object from MinIO: {}", e.getMessage());
        }
    }

    public String getBucketName() {
        return minioProperties.getBucketName();
    }

    public String getPresignedUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .expiry(minioProperties.getPresignedUrlExpiry(), TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }

    public int getPresignedUrlExpiry() {
        return minioProperties.getPresignedUrlExpiry();
    }
}
