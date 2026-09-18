package com.td.application.documents;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileResponse {

    @JsonProperty("file_id")
    private UUID fileId;

    @JsonProperty("document_id")
    private UUID documentId;

    @JsonProperty("original_file_name")
    private String originalFileName;

    @JsonProperty("mime_type")
    private String mimeType;

    @JsonProperty("file_size")
    private Long fileSize;

    @JsonProperty("upload_date")
    private LocalDateTime uploadDate;

    @JsonProperty("presigned_url")
    private String presignedUrl;

    @JsonProperty("presigned_expires_in_seconds")
    private Integer presignedExpiresInSeconds;
}