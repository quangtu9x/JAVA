package com.td.application.catalog.products;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class SearchProductsRequest {
    
    private String name;
    private String description;
    private UUID brandId;
    private String brandName;
    private BigDecimal minRate;
    private BigDecimal maxRate;
    
    @Min(value = 0, message = "Số trang không được âm")
    private int pageNumber = 0;
    
    @Min(value = 1, message = "Kích thước trang tối thiểu là 1")
    @Max(value = 100, message = "Kích thước trang không vượt quá 100")
    private int pageSize = 10;
    
    private String sortBy = "name";
    private String sortDirection = "asc";
}