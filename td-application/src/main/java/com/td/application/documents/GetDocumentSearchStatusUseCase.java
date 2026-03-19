package com.td.application.documents;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetDocumentSearchStatusUseCase {

    private final DocumentSearchService documentSearchService;

    public Result<DocumentSearchStatusDto> execute() {
        return Result.success(documentSearchService.getStatus());
    }
}
