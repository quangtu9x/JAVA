package com.td.application.catalog.brands;

import com.td.application.common.models.PaginationResponse;
import com.td.domain.catalog.Brand;
import com.td.infrastructure.persistence.repository.BrandRepository;
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
public class SearchBrandsUseCase {
    
    private final BrandRepository brandRepository;

    public PaginationResponse<BrandDto> execute(SearchBrandsRequest request) {
        try {
            // Build specification
            Specification<Brand> spec = Specification.where(null);
            
            if (request.getName() != null && !request.getName().trim().isEmpty()) {
                spec = spec.and(BrandRepository.withName(request.getName()));
            }
            
            if (request.getDescription() != null && !request.getDescription().trim().isEmpty()) {
                spec = spec.and(BrandRepository.withDescription(request.getDescription()));
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
            var page = brandRepository.findAll(spec, pageable, false);
            
            // Map to DTOs
            List<BrandDto> brandDtos = page.getContent().stream()
                .map(this::mapToDto)
                .toList();
            
            return new PaginationResponse<>(
                brandDtos,
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

    private BrandDto mapToDto(Brand brand) {
        var dto = new BrandDto();
        dto.setId(brand.getId());
        dto.setName(brand.getName());
        dto.setDescription(brand.getDescription());
        dto.setProductCount(brand.getProductCount());
        dto.setCreatedOn(brand.getCreatedOn());
        dto.setLastModifiedOn(brand.getLastModifiedOn());
        return dto;
    }
}