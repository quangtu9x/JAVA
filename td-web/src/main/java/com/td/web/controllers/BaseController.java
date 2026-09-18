package com.td.web.controllers;

import com.td.application.common.models.ApiResponse;
import com.td.application.common.models.CachedPaginationResponse;
import com.td.application.common.models.CachedResult;
import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public abstract class BaseController {

    protected <T> ResponseEntity<ApiResponse<T>> ok(Result<T> result) {
        return ResponseEntity.ok(toApiResponse(result));
    }

    protected <T> ResponseEntity<ApiResponse<T>> created(Result<T> result) {
        if (!result.isSuccess()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(result.getError()));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(result.getData()));
    }

    protected <T> ResponseEntity<ApiResponse<T>> ok(T body) {
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    protected <T> ResponseEntity<ApiResponse<List<T>>> ok(PaginationResponse<T> response) {
        return ResponseEntity.ok(toApiResponse(response));
    }

    protected <T> ResponseEntity<ApiResponse<T>> badRequest(Result<T> result) {
        return ResponseEntity.badRequest().body(ApiResponse.error(result.getError()));
    }

    protected <T> ResponseEntity<ApiResponse<Void>> noContent() {
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Chuyển {@link Result} thành {@link ApiResponse}, giữ nguyên metadata cache (nếu là {@link CachedResult})
     * vào field {@code properties} để FE nhận biết dữ liệu đến từ Redis cache.
     */
    protected <T> ApiResponse<T> toApiResponse(Result<T> result) {
        if (!result.isSuccess()) {
            return ApiResponse.error(result.getError());
        }
        if (result instanceof CachedResult<T> cached) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("cache", true);
            properties.put("cacheKey", cached.getCacheKey());
            return ApiResponse.success(result.getData(), properties);
        }
        return ApiResponse.success(result.getData());
    }

    /**
     * Chuyển {@link PaginationResponse} thành {@link ApiResponse}: {@code data} = danh sách item,
     * {@code total} = tổng số bản ghi, các thông tin phân trang còn lại (pageNumber, pageSize, totalPages,
     * first, last) và metadata cache (nếu là {@link CachedPaginationResponse}) đưa vào {@code properties}.
     */
    protected <T> ApiResponse<List<T>> toApiResponse(PaginationResponse<T> response) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("pageNumber", response.getPageNumber());
        properties.put("pageSize", response.getPageSize());
        properties.put("totalPages", response.getTotalPages());
        properties.put("first", response.isFirst());
        properties.put("last", response.isLast());
        if (response instanceof CachedPaginationResponse<T> cached) {
            properties.put("cache", true);
            properties.put("cacheKey", cached.getCacheKey());
        }
        return ApiResponse.success(response.getItems(), (int) response.getTotalItems(), properties);
    }
}
