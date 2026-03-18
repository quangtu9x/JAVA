package com.td.application.documents;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleFileDto {

    @JsonProperty("fileId")
    private UUID fileId;

    @JsonProperty("documentId")
    private UUID documentId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("size")
    private Long size;

    @JsonProperty("type")
    private String type;

    @JsonProperty("mimeType")
    private String mimeType;

    @JsonProperty("downloadUrl")
    private String downloadUrl;
}
