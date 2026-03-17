package com.td.application.catalog.products;

import com.td.application.common.interfaces.IRepository;
import com.td.application.common.models.Result;
import com.td.domain.catalog.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DeleteProductUseCase {
    
    private final IRepository<Product> productRepository;

    public Result<UUID> execute(DeleteProductRequest request) {
        try {
            // Tìm sản phẩm theo ID
            var existingProduct = productRepository.findById(request.getId());
            if (existingProduct.isEmpty()) {
                return Result.failure("Không tìm thấy sản phẩm với ID: " + request.getId());
            }

            // Xóa sản phẩm
            productRepository.delete(existingProduct.get());
            
            return Result.success(request.getId());
            
        } catch (Exception ex) {
            return Result.failure("Xóa sản phẩm thất bại: " + ex.getMessage());
        }
    }
}