package com.td.application.catalog.products;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ProductDto {
    private UUID id;
    private String name;
    private String description;
    private BigDecimal rate;
    private String imagePath;
    private UUID brandId;
    private String brandName;
    private LocalDateTime createdOn;
    private LocalDateTime lastModifiedOn;
}