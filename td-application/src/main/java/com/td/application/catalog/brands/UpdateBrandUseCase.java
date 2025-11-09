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
public class UpdateBrandUseCase {
    
    private final BrandRepository brandRepository;

    public Result<UUID> execute(UpdateBrandRequest request) {
        try {
            // Find existing brand
            var existingBrand = brandRepository.findById(request.getId());
            if (existingBrand.isEmpty()) {
                return Result.failure("Brand not found with ID: " + request.getId());
            }

            var brand = existingBrand.get();
            
            // Check if new name conflicts with existing brand (excluding current brand)
            if (!brand.getName().equalsIgnoreCase(request.getName())) {
                if (brandRepository.existsByNameIgnoreCase(request.getName())) {
                    return Result.failure("Brand with name '" + request.getName() + "' already exists");
                }
            }

            // Update brand
            brand.update(request.getName(), request.getDescription());

            // Save brand
            var savedBrand = brandRepository.save(brand);
            
            return Result.success(savedBrand.getId());
            
        } catch (Exception ex) {
            return Result.failure("Failed to update brand: " + ex.getMessage());
        }
    }
}