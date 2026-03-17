package com.td.application.catalog.brands;

import com.td.application.common.models.Result;
import com.td.domain.catalog.Brand;
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
            // Tìm thương hiệu theo ID
            var existingBrand = brandRepository.findById(request.getId());
            if (existingBrand.isEmpty()) {
                return Result.failure("Không tìm thấy thương hiệu với ID: " + request.getId());
            }

            var brand = existingBrand.get();
            
            // Kiểm tra tên mới có trùng với thương hiệu khác không
            if (!brand.getName().equalsIgnoreCase(request.getName())) {
                if (brandRepository.existsByNameIgnoreCase(request.getName())) {
                    return Result.failure("Thương hiệu '" + request.getName() + "' đã tồn tại");
                }
            }

            // Cập nhật thương hiệu
            brand.update(request.getName(), request.getDescription());

            // Lưu vào database
            var savedBrand = brandRepository.save(brand);
            
            return Result.success(savedBrand.getId());
            
        } catch (Exception ex) {
            return Result.failure("Cập nhật thương hiệu thất bại: " + ex.getMessage());
        }
    }
}