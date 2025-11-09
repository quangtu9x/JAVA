package com.td.application.catalog.products;

import lombok.Data;
import java.util.UUID;

@Data
public class DeleteProductRequest {
    private UUID id;
    
    public DeleteProductRequest(UUID id) {
        this.id = id;
    }
}