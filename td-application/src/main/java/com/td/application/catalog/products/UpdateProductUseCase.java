package com.td.application.catalog.products;

import com.td.application.common.interfaces.IRepository;
import com.td.application.common.models.Result;
import com.td.domain.catalog.Brand;
import com.td.domain.catalog.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UpdateProductUseCase {
    
    private final IRepository<Product> productRepository;
    private final IRepository<Brand> brandRepository;

    public Result<UUID> execute(UpdateProductRequest request) {
        try {
            // Find existing product
            var existingProduct = productRepository.findById(request.getId());
            if (existingProduct.isEmpty()) {
                return Result.failure("Product not found with ID: " + request.getId());
            }

            // Validate brand exists if different
            var product = existingProduct.get();
            if (!product.getBrandId().equals(request.getBrandId())) {
                var brand = brandRepository.findById(request.getBrandId());
                if (brand.isEmpty()) {
                    return Result.failure("Brand not found with ID: " + request.getBrandId());
                }
            }

            // Update product
            product.update(
                request.getName(),
                request.getDescription(),
                request.getRate(),
                request.getBrandId(),
                request.getImagePath()
            );

            // Save product
            var savedProduct = productRepository.save(product);
            
            return Result.success(savedProduct.getId());
            
        } catch (Exception ex) {
            return Result.failure("Failed to update product: " + ex.getMessage());
        }
    }
}