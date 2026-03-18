package com.td.infrastructure.documents;

import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.application.common.models.PaginationResponse;
import com.td.application.documents.FileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListDocumentFilesUseCaseImpl implements ListDocumentFilesUseCase {

    @Override
    public PaginationResponse<FileDto> execute(UUID documentId) {
        // TODO: Implement list files logic
        // 1. Validate document exists
        // 2. Query files from database
        // 3. Apply pagination
        // 4. Return paginated list
        return new PaginationResponse<>(
                new ArrayList<>(),  // items
                0,                  // pageNumber
                20,                 // pageSize
                0,                  // totalItems
                0,                  // totalPages
                true,               // first
                true                // last
        );
    }
}
