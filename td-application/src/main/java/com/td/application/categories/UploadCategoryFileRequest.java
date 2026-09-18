package com.td.application.categories;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadCategoryFileRequest {

    @NotNull(message = "Category ID không được để trống")
    @JsonProperty("category_id")
    private UUID categoryId;

    @NotNull(message = "File content không được để trống")
    @JsonProperty("file_content")
    private InputStream fileContent;

    @NotNull(message = "File name không được để trống")
    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("mime_type")
    private String mimeType;

    @JsonProperty("file_size")
    private Long fileSize;

    @JsonProperty("description")
    private String description;
}
