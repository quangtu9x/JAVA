package com.td.application.categories;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class SearchCategoriesRequest {

    private String keyword;
    private UUID parentId;
    private Integer level;
    private Boolean isActive;

    // BL-113: lọc theo 1 hoặc nhiều "form" cùng lúc (vd lấy chung cả `external_unit_level` lẫn
    // `external_unit` để dựng cây "Cơ quan ngoài tỉnh" trong 1 lần gọi) — Category dùng chung 1 bảng
    // cho nhiều loại danh mục phân biệt bằng `form`, khác Organization (bảng riêng, không cần lọc
    // form vì toàn bộ bảng đã là 1 loại cây).
    private List<String> forms;

    @Min(value = 0, message = "Page number phải >= 0")
    private int pageNumber = 0;

    @Min(value = 1, message = "Page size phải >= 1")
    @Max(value = 200, message = "Page size tối đa 200")
    private int pageSize = 20;

    private String sortBy = "sortOrder";
    private String sortDirection = "asc";
}
