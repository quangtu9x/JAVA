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
public class FileDto {

    @JsonProperty("file_id")
    private UUID fileId;

    @JsonProperty("document_id")
    private UUID documentId;

    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("original_file_name")
    private String originalFileName;

    @JsonProperty("file_size")
    private Long fileSize;

    @JsonProperty("mime_type")
    private String mimeType;

    @JsonProperty("upload_date")
    private LocalDateTime uploadDate;

    @JsonProperty("uploaded_by")
    private String uploadedBy;

    @JsonProperty("storage_path")
    private String storagePath;

    @JsonProperty("is_primary")
    private Boolean isPrimary;

    @JsonProperty("version")
    private Integer version;

    @JsonProperty("description")
    private String description;

    @JsonProperty("checksum")
    private String checksum;
}
