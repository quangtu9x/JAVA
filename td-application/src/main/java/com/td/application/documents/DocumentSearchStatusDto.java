package com.td.application.documents;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DocumentSearchStatusDto {
    private boolean enabled;
    private boolean available;
    private String backend;
    private String indexName;
    private long indexedDocuments;
    private String message;
}
