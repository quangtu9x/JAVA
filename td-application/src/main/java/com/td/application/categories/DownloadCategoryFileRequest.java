package com.td.application.categories;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class DownloadCategoryFileRequest {

    @NotNull(message = "Category ID không được để trống")
    @JsonProperty("category_id")
    private UUID categoryId;

    @NotNull(message = "File ID không được để trống")
    @JsonProperty("file_id")
    private UUID fileId;
}
