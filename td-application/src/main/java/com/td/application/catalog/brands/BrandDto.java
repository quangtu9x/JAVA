package com.td.application.catalog.brands;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class BrandDto {
    private UUID id;
    private String name;
    private String description;
    private int productCount;
    private LocalDateTime createdOn;
    private LocalDateTime lastModifiedOn;
}