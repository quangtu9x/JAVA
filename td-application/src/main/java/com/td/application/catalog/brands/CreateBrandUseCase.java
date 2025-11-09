package com.td.application.catalog.brands;

import com.td.application.common.interfaces.IRepository;
import com.td.application.common.models.Result;
import com.td.domain.catalog.Brand;
import com.td.infrastructure.persistence.repository.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CreateBrandUseCase {
    
    private final BrandRepository brandRepository;

    public Result<UUID> execute(CreateBrandRequest request) {
        try {
            // Check if brand name already exists
            if (brandRepository.existsByNameIgnoreCase(request.getName())) {
                return Result.failure("Brand with name '" + request.getName() + "' already exists");
            }

            // Create brand
            var brand = new Brand(request.getName(), request.getDescription());

            // Save brand
            var savedBrand = brandRepository.save(brand);
            
            return Result.success(savedBrand.getId());
            
        } catch (Exception ex) {
            return Result.failure("Failed to create brand: " + ex.getMessage());
        }
    }
}