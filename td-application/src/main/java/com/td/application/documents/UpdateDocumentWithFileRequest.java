package com.td.application.documents;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDocumentWithFileRequest {

    @JsonProperty("document_id")
    private UUID documentId;

    @Size(min = 2, max = 300, message = "Tiêu đề phải từ 2 đến 300 ký tự")
    @JsonProperty("title")
    private String title;

    @Size(max = 100, message = "Loại tài liệu không vượt quá 100 ký tự")
    @JsonProperty("document_type")
    private String documentType;

    @Size(max = 50, message = "Trạng thái không vượt quá 50 ký tự")
    @JsonProperty("status")
    private String status;

    @JsonProperty("content")
    private String content;

    @JsonProperty("tags")
    private List<String> tags;

    @JsonProperty("attributes")
    private Map<String, Object> attributes;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    @JsonProperty("file_content")
    private InputStream fileContent;

    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("mime_type")
    private String mimeType;

    @JsonProperty("file_size")
    private Long fileSize;

    @JsonProperty("is_primary_file")
    private Boolean isPrimaryFile;

    @JsonProperty("file_description")
    private String fileDescription;
}
