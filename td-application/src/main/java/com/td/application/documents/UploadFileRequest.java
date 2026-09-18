package com.td.application.documents;

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
public class UploadFileRequest {

    @NotNull(message = "Document ID không được để trống")
    @JsonProperty("document_id")
    private UUID documentId;

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

    @JsonProperty("is_primary")
    private Boolean isPrimary;

    @JsonProperty("description")
    private String description;

    @JsonProperty("version")
    private Integer version;

    // DT-007 (27/08/2026): id vet xu ly (vd draft_process_documents.id) neu tep nay duoc dinh kem
    // CUNG LUC voi 1 lan xu ly cu the - xem javadoc FileMetadataEntity#processId. Tuy chon, hau het
    // module khac khong dung.
    @JsonProperty("process_id")
    private UUID processId;

    // Bugfix 27/08/2026 (phan hoi nguoi dung: tach duong dan MinIO theo tenant/module de tranh lan
    // giua cac module khi dung chung 1 bucket): nhan dien module cua tai lieu (vd "duthao"/"vbden"/
    // "vbdi") de UploadFileUseCaseImpl chen vao object path. Tuy chon - null/rong fallback ve
    // "documents" (khop hanh vi cu, KHONG doi duong dan cua cac call site chua cap nhat).
    @JsonProperty("module")
    private String module;
}
