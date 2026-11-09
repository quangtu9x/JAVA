package com.td.application.sharedcore;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.UUID;

@Data
public class SearchOrganizationsRequest {

    private String keyword;
    private UUID parentId;
    private Integer level;
    private Boolean isActive;

    // 30/10/2026: lọc theo loại node (agency_level/agency/unit/department) — màn quản trị Cấp cơ quan/
    // Cơ quan/Đơn vị thay Domino qlvb/search search:{form}. null/rỗng = không lọc.
    private String form;

    @Min(value = 0, message = "Page number phải >= 0")
    private int pageNumber = 0;

    @Min(value = 1, message = "Page size phải >= 1")
    @Max(value = 200, message = "Page size tối đa 200")
    private int pageSize = 20;

    private String sortBy = "sortOrder";
    private String sortDirection = "asc";
}
