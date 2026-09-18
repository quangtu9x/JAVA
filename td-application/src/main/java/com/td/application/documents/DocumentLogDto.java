package com.td.application.documents;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class DocumentLogDto {

    private UUID id;
    private UUID documentId;
    private String logType;
    private Map<String, Object> logData;
    private String createdBy;
    private LocalDateTime createdAt;
}
