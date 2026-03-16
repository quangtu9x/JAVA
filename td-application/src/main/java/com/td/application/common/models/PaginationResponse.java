package com.td.application.common.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Generic paginated response payload.
 */
@Getter
@AllArgsConstructor
public class PaginationResponse<T> {

    private final List<T> items;
    private final int pageNumber;
    private final int pageSize;
    private final long totalItems;
    private final int totalPages;
    private final boolean first;
    private final boolean last;
}