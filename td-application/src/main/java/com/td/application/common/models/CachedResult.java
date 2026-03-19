package com.td.application.common.models;

/**
 * Result với field cache=true để frontend/gateway nhận biết
 * dữ liệu được lấy từ Redis cache thay vì truy vấn DB trực tiếp.
 */
public class CachedResult<T> extends Result<T> {

    private final boolean cache = true;
    private final String cacheKey;

    public CachedResult(Result<T> source, String cacheKey) {
        super(source.isSuccess(), source.getData(), source.getError());
        this.cacheKey = cacheKey;
    }

    public boolean isCache() {
        return cache;
    }

    public String getCacheKey() {
        return cacheKey;
    }
}
