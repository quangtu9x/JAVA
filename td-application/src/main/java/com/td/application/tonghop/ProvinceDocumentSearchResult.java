package com.td.application.tonghop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Kết quả tìm kiếm + bộ đếm theo đúng bộ lọc hiện tại ({@code facets}) — FE vẽ biểu đồ/nhãn đếm từ đây,
 * không còn tự cộng từ {@code location.state} như bản cũ (mất số liệu khi F5).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvinceDocumentSearchResult {

    private List<ProvinceDocumentItem> items;
    private long totalItems;
    private int pageNumber;
    private int pageSize;
    /** Nhóm → (giá trị → số lượng): {@code kind}, {@code processState}, {@code channel}, {@code flags}. */
    private Map<String, Map<String, Long>> facets;
}
