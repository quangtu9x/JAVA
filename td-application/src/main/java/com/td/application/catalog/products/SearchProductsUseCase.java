package com.td.application.catalog.products;

import com.td.application.common.interfaces.IRepository;
import com.td.application.common.models.PaginationResponse;
import com.td.domain.catalog.Product;
import com.td.infrastructure.persistence.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchProductsUseCase {
    
    private final ProductRepository productRepository;

    public PaginationResponse<ProductDto> execute(SearchProductsRequest request) {
        try {
            // Build specification
            Specification<Product> spec = Specification.where(null);
            
            if (request.getName() != null && !request.getName().trim().isEmpty()) {
                spec = spec.and(ProductRepository.withName(request.getName()));
            }
            
            if (request.getDescription() != null && !request.getDescription().trim().isEmpty()) {
                spec = spec.and(ProductRepository.withDescription(request.getDescription()));
            }
            
            if (request.getBrandId() != null) {
                spec = spec.and(ProductRepository.withBrandId(request.getBrandId()));
            }
            
            if (request.getBrandName() != null && !request.getBrandName().trim().isEmpty()) {
                spec = spec.and(ProductRepository.withBrandName(request.getBrandName()));
            }
            
            if (request.getMinRate() != null || request.getMaxRate() != null) {
                spec = spec.and(ProductRepository.withRateRange(request.getMinRate(), request.getMaxRate()));
            }

            // Create pageable
            Sort sort = Sort.by(
                "desc".equalsIgnoreCase(request.getSortDirection()) 
                    ? Sort.Direction.DESC 
                    : Sort.Direction.ASC, 
                request.getSortBy()
            );
            
            Pageable pageable = PageRequest.of(request.getPageNumber(), request.getPageSize(), sort);
            
            // Execute query
            var page = productRepository.findAll(spec, pageable, false);
            
            // Map to DTOs
            List<ProductDto> productDtos = page.getContent().stream()
                .map(this::mapToDto)
                .toList();
            
            return new PaginationResponse<>(
                productDtos,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
            );
            
        } catch (Exception ex) {
            return new PaginationResponse<>(
                List.of(),
                request.getPageNumber(),
                request.getPageSize(),
                0L,
                0,
                true,
                true
            );
        }
    }

    private ProductDto mapToDto(Product product) {
        var dto = new ProductDto();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setRate(product.getRate());
        dto.setImagePath(product.getImagePath());
        dto.setBrandId(product.getBrandId());
        if (product.getBrand() != null) {
            dto.setBrandName(product.getBrand().getName());
        }
        dto.setCreatedOn(product.getCreatedOn());
        dto.setLastModifiedOn(product.getLastModifiedOn());
        return dto;
    }
}