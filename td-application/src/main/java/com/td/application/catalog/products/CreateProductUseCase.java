package com.td.application.catalog.products;

import com.td.application.common.interfaces.IRepository;
import com.td.application.common.models.Result;
import com.td.domain.catalog.Brand;
import com.td.domain.catalog.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Use Case - Tạo sản phẩm mới trong hệ thống
 * 
 * Nghiệp vụ:
 * 1. Validate thương hiệu có tồn tại không
 * 2. Tạo entity Product mới với thông tin từ request
 * 3. Lưu vào database
 * 4. Trả về ID của sản phẩm vừa tạo
 * 
 * Sử dụng:
 * - Input: CreateProductRequest (name, description, rate, brandId, imagePath)
 * - Output: Result<UUID> (success với productId hoặc failure với error message)
 * 
 * @Transactional: Đảm bảo toàn bộ thao tác trong 1 transaction
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CreateProductUseCase {
    
    private final IRepository<Product> productRepository;
    private final IRepository<Brand> brandRepository;

    /**
     * Thực thi use case - Tạo sản phẩm mới
     * 
     * @param request Request chứa thông tin sản phẩm cần tạo
     * @return Result<UUID> - Success với ID sản phẩm hoặc Failure với lỗi
     */
    public Result<UUID> execute(CreateProductRequest request) {
        try {
            // Bước 1: Kiểm tra thương hiệu có tồn tại không
            var brand = brandRepository.findById(request.getBrandId());
            if (brand.isEmpty()) {
                return Result.failure("Không tìm thấy thương hiệu với ID: " + request.getBrandId());
            }

            // Bước 2: Create product - Tạo entity sản phẩm mới
            var product = new Product(
                request.getName(),
                request.getDescription(), 
                request.getRate(),
                request.getBrandId(),
                request.getImagePath()
            );

            // Bước 3: Save product - Lưu vào database
            var savedProduct = productRepository.save(product);
            
            // Bước 4: Return product ID - Trả về ID sản phẩm vừa tạo
            return Result.success(savedProduct.getId());
            
        } catch (Exception ex) {
            return Result.failure("Tạo sản phẩm thất bại: " + ex.getMessage());
        }
    }
}