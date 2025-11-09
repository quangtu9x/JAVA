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
            // Find existing product
            var existingProduct = productRepository.findById(request.getId());
            if (existingProduct.isEmpty()) {
                return Result.failure("Product not found with ID: " + request.getId());
            }

            // Delete product (soft delete if implemented)
            productRepository.delete(existingProduct.get());
            
            return Result.success(request.getId());
            
        } catch (Exception ex) {
            return Result.failure("Failed to delete product: " + ex.getMessage());
        }
    }
}