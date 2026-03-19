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

    @Min(value = 0, message = "Page number phải >= 0")
    private int pageNumber = 0;

    @Min(value = 1, message = "Page size phải >= 1")
    @Max(value = 200, message = "Page size tối đa 200")
    private int pageSize = 20;

    private String sortBy = "fullName";
    private String sortDirection = "asc";
}
