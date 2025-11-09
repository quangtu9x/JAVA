package com.td.application.catalog.brands;

import com.td.application.common.interfaces.IRepository;
import com.td.application.common.models.Result;
import com.td.domain.catalog.Brand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetBrandUseCase {
    
    private final IRepository<Brand> brandRepository;

    public Result<BrandDto> execute(GetBrandRequest request) {
        try {
            // Find brand
            var brand = brandRepository.findById(request.getId());
            if (brand.isEmpty()) {
                return Result.failure("Brand not found with ID: " + request.getId());
            }

            var brandEntity = brand.get();
            var brandDto = new BrandDto();
            
            // Map properties
            brandDto.setId(brandEntity.getId());
            brandDto.setName(brandEntity.getName());
            brandDto.setDescription(brandEntity.getDescription());
            brandDto.setProductCount(brandEntity.getProductCount());
            brandDto.setCreatedOn(brandEntity.getCreatedOn());
            brandDto.setLastModifiedOn(brandEntity.getLastModifiedOn());
            
            return Result.success(brandDto);
            
        } catch (Exception ex) {
            return Result.failure("Failed to get brand: " + ex.getMessage());
        }
    }
}