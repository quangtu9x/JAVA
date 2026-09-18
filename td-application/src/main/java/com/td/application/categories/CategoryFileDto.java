package com.td.application.categories;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

// BL-118: DTO tệp đính kèm cho "danh mục dùng chung" (Category — Banner/Footer/Hướng dẫn sử dụng).
// Tên riêng (không tái dùng FileDto của Document) vì JSON field cần gắn với "category_id", không
// phải "document_id" — xem CategoryFileMetadata.java.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryFileDto {

    @JsonProperty("file_id")
    private UUID fileId;

    @JsonProperty("category_id")
    private UUID categoryId;

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

    @JsonProperty("description")
    private String description;
}
