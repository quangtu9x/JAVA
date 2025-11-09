package com.td.application.catalog.brands;

import lombok.Data;
import java.util.UUID;

@Data
public class GetBrandRequest {
    private UUID id;
    
    public GetBrandRequest(UUID id) {
        this.id = id;
    }
}