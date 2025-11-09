package com.td.application.catalog.products;

import lombok.Data;

@Data
public class ExportProductsRequest {
    private String name;
    private String brandName;
    private String description;
}