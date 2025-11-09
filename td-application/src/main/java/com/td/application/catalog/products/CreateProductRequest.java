package com.td.application.catalog.products;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateProductRequest {
    
    @NotBlank(message = "Product name is required")
    @Size(min = 2, max = 100, message = "Product name must be between 2 and 100 characters")
    private String name;
    
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;
    
    @NotNull(message = "Rate is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Rate must be greater than 0")
    @Digits(integer = 16, fraction = 2, message = "Rate must have maximum 16 integer digits and 2 decimal places")
    private BigDecimal rate;
    
    @NotNull(message = "Brand ID is required")
    private UUID brandId;
    
    private String imagePath;
}