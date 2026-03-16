package com.td.application.storage;

import com.td.domain.storage.FileCategory;
import com.td.domain.storage.FileMetadata;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

public interface MinIOService {

    FileMetadata uploadFile(MultipartFile file, FileCategory category, UUID uploadedBy, String description);

    InputStream downloadFile(String filePath);

    void deleteFile(String filePath);

    String generatePresignedDownloadUrl(String filePath, int expiryMinutes);
}