package com.td.application.categories;

import com.td.application.common.models.PaginationResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryListCacheEntry {

    private List<CategoryDto> items = Collections.emptyList();
    private int pageNumber;
    private int pageSize;
    private long totalItems;
    private int totalPages;
    private boolean first;
    private boolean last;

    public static CategoryListCacheEntry from(PaginationResponse<CategoryDto> response) {
        if (response == null) return null;
        return CategoryListCacheEntry.builder()
            .items(response.getItems() == null ? Collections.emptyList() : response.getItems())
            .pageNumber(response.getPageNumber())
            .pageSize(response.getPageSize())
            .totalItems(response.getTotalItems())
            .totalPages(response.getTotalPages())
            .first(response.isFirst())
            .last(response.isLast())
            .build();
    }

    public PaginationResponse<CategoryDto> toPaginationResponse() {
        return new PaginationResponse<>(
            items == null ? Collections.emptyList() : items,
            pageNumber, pageSize, totalItems, totalPages, first, last);
    }
}
