package com.td.application.catalog.brands;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.util.UUID;

@Data
public class UpdateBrandRequest {
    
    @NotNull(message = "Brand ID is required")
    private UUID id;
    
    @NotBlank(message = "Brand name is required")
    @Size(min = 2, max = 100, message = "Brand name must be between 2 and 100 characters")
    private String name;
    
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;
}