package com.td.application.sharedcore;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.UUID;

@Data
public class SearchAppUsersRequest {

    private String keyword;
    private UUID organizationId;
    private Boolean isActive;

    // BL-112: "Kiêm nhiệm" — lọc danh sách chức vụ kiêm nhiệm của đúng 1 người dùng chính
    // (AppUser.parentUserId, xem ChiTietModalNguoiDungKiemNhiem.jsx). null = không lọc theo cha
    // (mặc định danh sách "Người dùng" vẫn trộn chung chính+kiêm nhiệm, đúng hành vi Domino gốc).
    private UUID parentUserId;

    // 06/10/2026: chỉ lấy các tài khoản trong danh sách này (null/rỗng = không lọc) - xem
    // PostgresAppUserRepository#usernamesFilter.
    private java.util.List<String> usernames;

    @Min(value = 0, message = "Page number phải >= 0")
    private int pageNumber = 0;

    @Min(value = 1, message = "Page size phải >= 1")
    @Max(value = 200, message = "Page size tối đa 200")
    private int pageSize = 20;

    // 24/09/2026: mặc định theo "Thứ tự hiển thị", sau đó Họ tên A-Z, Tên đăng nhập (xem
    // SearchAppUsersUseCase.buildPageable). Hằng số dùng chung cho @RequestParam(defaultValue) ở controller.
    public static final String DEFAULT_SORT_BY = "sortOrder";

    private String sortBy = DEFAULT_SORT_BY;
    private String sortDirection = "asc";
}
