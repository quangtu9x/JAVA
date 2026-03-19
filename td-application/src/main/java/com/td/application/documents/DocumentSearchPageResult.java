package com.td.application.documents;

import com.td.application.common.models.PaginationResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DocumentSearchPageResult {
    private final PaginationResponse<DocumentDto> page;
    private final String backend;
}
