package com.td.application.documents;

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
public class DocumentListCacheEntry {

    private List<DocumentDto> items = Collections.emptyList();
    private int pageNumber;
    private int pageSize;
    private long totalItems;
    private int totalPages;
    private boolean first;
    private boolean last;

    public static DocumentListCacheEntry from(PaginationResponse<DocumentDto> response) {
        if (response == null) {
            return null;
        }

        return DocumentListCacheEntry.builder()
            .items(response.getItems() == null ? Collections.emptyList() : response.getItems())
            .pageNumber(response.getPageNumber())
            .pageSize(response.getPageSize())
            .totalItems(response.getTotalItems())
            .totalPages(response.getTotalPages())
            .first(response.isFirst())
            .last(response.isLast())
            .build();
    }

    public PaginationResponse<DocumentDto> toPaginationResponse() {
        return new PaginationResponse<>(
            items == null ? Collections.emptyList() : items,
            pageNumber,
            pageSize,
            totalItems,
            totalPages,
            first,
            last
        );
    }
}