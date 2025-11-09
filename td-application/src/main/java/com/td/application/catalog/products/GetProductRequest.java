package com.td.application.catalog.products;

import lombok.Data;
import java.util.UUID;

@Data
public class GetProductRequest {
    private UUID id;
    
    public GetProductRequest(UUID id) {
        this.id = id;
    }
}