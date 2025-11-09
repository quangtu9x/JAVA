package com.td.application.catalog.brands;

import com.td.application.common.interfaces.IRepository;
import com.td.application.common.models.Result;
import com.td.domain.catalog.Brand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DeleteBrandUseCase {
    
    private final IRepository<Brand> brandRepository;

    public Result<UUID> execute(DeleteBrandRequest request) {
        try {
            // Find existing brand
            var existingBrand = brandRepository.findById(request.getId());
            if (existingBrand.isEmpty()) {
                return Result.failure("Brand not found with ID: " + request.getId());
            }

            var brand = existingBrand.get();
            
            // Check if brand has products
            if (brand.hasProducts()) {
                return Result.failure("Cannot delete brand that has products. Please delete or reassign products first.");
            }

            // Delete brand
            brandRepository.delete(brand);
            
            return Result.success(request.getId());
            
        } catch (Exception ex) {
            return Result.failure("Failed to delete brand: " + ex.getMessage());
        }
    }
}