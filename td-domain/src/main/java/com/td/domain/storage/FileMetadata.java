package com.td.domain.storage;

import com.td.domain.common.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "file_metadata")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadata extends BaseEntity {

    @Id
    private UUID id;

    @Column(name = "original_filename", nullable = false)
    @NotBlank(message = "Original filename cannot be blank")
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, unique = true)
    @NotBlank(message = "Stored filename cannot be blank")
    private String storedFilename;

    @Column(name = "file_path", nullable = false)
    @NotBlank(message = "File path cannot be blank")
    private String filePath;

    @Column(name = "file_size", nullable = false)
    @NotNull(message = "File size cannot be null")
    private Long fileSize;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_extension")
    private String fileExtension;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_category", nullable = false)
    @NotNull(message = "File category cannot be null")
    private FileCategory fileCategory;

    @Column(name = "bucket_name", nullable = false)
    @NotBlank(message = "Bucket name cannot be blank")
    private String bucketName;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    @NotNull(message = "Upload timestamp cannot be null")
    private Instant uploadedAt;

    @Column(name = "download_count")
    private Long downloadCount = 0L;

    @Column(name = "last_downloaded_at")
    private Instant lastDownloadedAt;

    @Column(name = "is_public")
    private Boolean isPublic = false;

    @Column(name = "description")
    private String description;

    @Column(name = "tags")
    private String tags; // JSON array of tags

    @Column(name = "metadata")
    private String metadata; // Additional JSON metadata

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (uploadedAt == null) {
            uploadedAt = Instant.now();
        }
    }

    // Business methods
    public void incrementDownloadCount() {
        this.downloadCount = (this.downloadCount != null ? this.downloadCount : 0) + 1;
        this.lastDownloadedAt = Instant.now();
    }

    public String getFileExtensionFromFilename() {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
    }

    public boolean isImage() {
        String ext = getFileExtensionFromFilename();
        return ext.matches("jpg|jpeg|png|gif|bmp|webp");
    }

    public boolean isPdf() {
        return "pdf".equalsIgnoreCase(getFileExtensionFromFilename());
    }

    public boolean isDocument() {
        String ext = getFileExtensionFromFilename();
        return ext.matches("doc|docx|xls|xlsx|ppt|pptx|txt|rtf");
    }

    public String getHumanReadableSize() {
        if (fileSize == null) return "Unknown";
        
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        if (fileSize < 1024 * 1024 * 1024) return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        return String.format("%.1f GB", fileSize / (1024.0 * 1024.0 * 1024.0));
    }
}