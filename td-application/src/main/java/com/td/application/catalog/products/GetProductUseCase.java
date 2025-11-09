package com.td.application.catalog.products;

import com.td.application.common.interfaces.IRepository;
import com.td.application.common.models.Result;
import com.td.domain.catalog.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetProductUseCase {
    
    private final IRepository<Product> productRepository;

    public Result<ProductDetailsDto> execute(GetProductRequest request) {
        try {
            // Find product with brand
            var product = productRepository.findById(request.getId());
            if (product.isEmpty()) {
                return Result.failure("Product not found with ID: " + request.getId());
            }

            var productEntity = product.get();
            var productDto = new ProductDetailsDto();
            
            // Map basic properties
            productDto.setId(productEntity.getId());
            productDto.setName(productEntity.getName());
            productDto.setDescription(productEntity.getDescription());
            productDto.setRate(productEntity.getRate());
            productDto.setImagePath(productEntity.getImagePath());
            productDto.setBrandId(productEntity.getBrandId());
            productDto.setCreatedOn(productEntity.getCreatedOn());
            productDto.setLastModifiedOn(productEntity.getLastModifiedOn());
            
            // Map brand properties if available
            if (productEntity.getBrand() != null) {
                productDto.setBrandName(productEntity.getBrand().getName());
                productDto.setBrandDescription(productEntity.getBrand().getDescription());
                productDto.setTotalProductsInBrand(productEntity.getBrand().getProductCount());
            }
            
            // Business properties
            productDto.setExpensive(productEntity.isExpensive());
            
            return Result.success(productDto);
            
        } catch (Exception ex) {
            return Result.failure("Failed to get product: " + ex.getMessage());
        }
    }
}