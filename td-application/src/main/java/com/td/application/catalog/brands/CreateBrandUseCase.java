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
public class CreateBrandUseCase {
    
    private final BrandRepository brandRepository;

    public Result<UUID> execute(CreateBrandRequest request) {
        try {
            // Kiểm tra tên thương hiệu đã tồn tại chưa
            if (brandRepository.existsByNameIgnoreCase(request.getName())) {
                return Result.failure("Thương hiệu '" + request.getName() + "' đã tồn tại");
            }

            // Tạo thương hiệu mới
            var brand = new Brand(request.getName(), request.getDescription());

            // Lưu vào database
            var savedBrand = brandRepository.save(brand);
            
            return Result.success(savedBrand.getId());
            
        } catch (Exception ex) {
            return Result.failure("Tạo thương hiệu thất bại: " + ex.getMessage());
        }
    }
}