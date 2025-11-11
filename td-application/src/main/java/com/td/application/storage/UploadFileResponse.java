package com.td.application.storage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadFileResponse {
    private UUID fileId;
    private String originalFilename;
    private String storedFilename;
    private String filePath;
    private Long fileSize;
    private String humanReadableSize;
    private String contentType;
    private String downloadUrl;
    private String message;
    private boolean success;
    
    public static UploadFileResponse success(FileMetadataDto fileMetadata, String downloadUrl) {
        UploadFileResponse response = new UploadFileResponse();
        response.fileId = fileMetadata.getId();
        response.originalFilename = fileMetadata.getOriginalFilename();
        response.storedFilename = fileMetadata.getStoredFilename();
        response.filePath = fileMetadata.getFilePath();
        response.fileSize = fileMetadata.getFileSize();
        response.humanReadableSize = fileMetadata.getHumanReadableSize();
        response.contentType = fileMetadata.getContentType();
        response.downloadUrl = downloadUrl;
        response.message = "File uploaded successfully";
        response.success = true;
        return response;
    }
    
    public static UploadFileResponse failure(String message) {
        UploadFileResponse response = new UploadFileResponse();
        response.message = message;
        response.success = false;
        return response;
    }
}