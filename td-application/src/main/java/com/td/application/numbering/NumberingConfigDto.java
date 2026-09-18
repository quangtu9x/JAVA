package com.td.application.numbering;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class NumberingConfigDto {

    private UUID id;
    private String documentType;
    private String format;
    private String resetPolicy;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
