package com.td.application.catalog.products;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductDetailsDto extends ProductDto {
    private String brandDescription;
    private int totalProductsInBrand;
    private boolean isExpensive;
}