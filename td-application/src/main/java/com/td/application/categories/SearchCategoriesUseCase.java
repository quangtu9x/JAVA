package com.td.application.categories;

import com.td.application.common.models.PaginationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchCategoriesUseCase {

    private final CategoryRepository categoryRepository;

    public PaginationResponse<CategoryDto> execute(SearchCategoriesRequest request) {
        try {
            Pageable pageable = buildPageable(request);
            var page = categoryRepository.search(request, pageable);

            List<CategoryDto> items = page.getContent().stream()
                .map(CategoryDtoMapper::map)
                .toList();

            return new PaginationResponse<>(
                items,
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
                Math.max(0, request.getPageNumber()),
                Math.min(Math.max(1, request.getPageSize()), 200),
                0L, 0, true, true
            );
        }
    }

    private Pageable buildPageable(SearchCategoriesRequest req) {
        String sortBy = (req.getSortBy() == null || req.getSortBy().isBlank())
            ? "sortOrder" : req.getSortBy();

        Sort.Direction direction = "desc".equalsIgnoreCase(req.getSortDirection())
            ? Sort.Direction.DESC : Sort.Direction.ASC;

        return PageRequest.of(
            Math.max(0, req.getPageNumber()),
            Math.min(Math.max(1, req.getPageSize()), 200),
            Sort.by(direction, sortBy)
        );
    }
}
