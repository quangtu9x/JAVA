package com.td.application.storage;

import com.td.domain.storage.FileCategory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadataDto {
    private UUID id;
    private String originalFilename;
    private String storedFilename;
    private String filePath;
    private Long fileSize;
    private String humanReadableSize;
    private String contentType;
    private String fileExtension;
    private FileCategory fileCategory;
    private String bucketName;
    private UUID uploadedBy;
    private Instant uploadedAt;
    private Long downloadCount;
    private Instant lastDownloadedAt;
    private Boolean isPublic;
    private String description;
    private String tags;
    private String downloadUrl;
    
    // Computed properties
    private boolean isImage;
    private boolean isPdf;
    private boolean isDocument;
}