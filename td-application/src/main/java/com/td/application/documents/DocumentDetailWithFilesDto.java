package com.td.application.documents;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDetailWithFilesDto {

    @JsonProperty("document")
    private DocumentDto document;

    @JsonProperty("files")
    private List<FileDto> files;
}
