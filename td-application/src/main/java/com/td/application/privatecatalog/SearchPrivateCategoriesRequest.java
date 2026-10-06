package com.td.application.privatecatalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class SearchPrivateCategoriesRequest {

    private String keyword;
    private UUID parentId;
    private Integer level;
    private Boolean isActive;

    // Lọc theo 1 hoặc nhiều "form" cùng lúc — cùng convention SearchCategoriesRequest (BL-113).
    private List<String> forms;

    // Mã định danh đơn vị/phòng ban (currentUser.identifier bên FE) — xem PrivateCategory.identifier.
    // Dùng cho các danh mục riêng theo đơn vị con trong cùng 1 tenant (vd: ký hiệu văn bản).
    private String identifier;

    // 06/10/2026: true = bỏ thành viên đã bị Khoá/Xoá khỏi receiver_obj (màn CHỌN người, vd chuyển
    // xử lý). Mặc định không lọc - màn quản trị nhóm người nhận vẫn thấy đủ thành viên đã lưu.
    private Boolean activeMembersOnly;

    @Min(value = 0, message = "Page number phải >= 0")
    private int pageNumber = 0;

    @Min(value = 1, message = "Page size phải >= 1")
    @Max(value = 200, message = "Page size tối đa 200")
    private int pageSize = 20;

    private String sortBy = "sortOrder";
    private String sortDirection = "asc";
}
