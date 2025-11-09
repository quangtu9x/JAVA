package com.td.application.catalog.brands;

import lombok.Data;
import java.util.UUID;

@Data
public class DeleteBrandRequest {
    private UUID id;
    
    public DeleteBrandRequest(UUID id) {
        this.id = id;
    }
}