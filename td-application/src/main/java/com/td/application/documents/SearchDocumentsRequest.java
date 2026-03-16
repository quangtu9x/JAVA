package com.td.application.documents;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class SearchDocumentsRequest {

    private String keyword;

    private String documentType;

    private String status;

    // Supports both legacy equals and advanced operators.
    // Example:
    // {
    //   "department": "Accounting",
    //   "title": {"operator": "contains", "value": "process"},
    //   "amount": {"operator": "range", "from": 10, "to": 100}
    // }
    private Map<String, Object> attributeFilters = new HashMap<>();

    @Min(value = 0, message = "Page number must be non-negative")
    private int pageNumber = 0;

    @Min(value = 1, message = "Page size must be at least 1")
    @Max(value = 100, message = "Page size cannot exceed 100")
    private int pageSize = 10;

    private String sortBy = "lastModifiedOn";

    private String sortDirection = "desc";
}
