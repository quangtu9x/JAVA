package com.td.application.common.models;

import java.util.List;

/**
 * PaginationResponse với field cache=true để frontend/gateway nhận biết
 * dữ liệu được lấy từ Redis cache thay vì truy vấn DB trực tiếp.
 */
public class CachedPaginationResponse<T> extends PaginationResponse<T> {

    private final boolean cache = true;
    private final String cacheKey;

    public CachedPaginationResponse(PaginationResponse<T> source, String cacheKey) {
        super(
            source.getItems(),
            source.getPageNumber(),
            source.getPageSize(),
            source.getTotalItems(),
            source.getTotalPages(),
            source.isFirst(),
            source.isLast()
        );
        this.cacheKey = cacheKey;
    }

    public boolean isCache() {
        return cache;
    }

    public String getCacheKey() {
        return cacheKey;
    }
}
