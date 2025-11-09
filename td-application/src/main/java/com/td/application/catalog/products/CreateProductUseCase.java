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
public class CreateProductUseCase {
    
    private final IRepository<Product> productRepository;
    private final IRepository<Brand> brandRepository;

    public Result<UUID> execute(CreateProductRequest request) {
        try {
            // Validate brand exists
            var brand = brandRepository.findById(request.getBrandId());
            if (brand.isEmpty()) {
                return Result.failure("Brand not found with ID: " + request.getBrandId());
            }

            // Create product
            var product = new Product(
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
            return Result.failure("Failed to create product: " + ex.getMessage());
        }
    }
}