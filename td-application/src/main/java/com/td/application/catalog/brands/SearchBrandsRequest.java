package com.td.application.catalog.brands;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

@Data
public class SearchBrandsRequest {
    
    private String name;
    private String description;
    
    @Min(value = 0, message = "Page number must be non-negative")
    private int pageNumber = 0;
    
    @Min(value = 1, message = "Page size must be at least 1")
    @Max(value = 100, message = "Page size cannot exceed 100")
    private int pageSize = 10;
    
    private String sortBy = "name";
    private String sortDirection = "asc";
}