package com.td.application.documents;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenameFileRequest {

    @NotNull(message = "Document ID không được để trống")
    @JsonProperty("document_id")
    private UUID documentId;

    @NotNull(message = "File ID không được để trống")
    @JsonProperty("file_id")
    private UUID fileId;

    @NotBlank(message = "Tên tệp mới không được để trống")
    @JsonProperty("new_file_name")
    private String newFileName;
}
