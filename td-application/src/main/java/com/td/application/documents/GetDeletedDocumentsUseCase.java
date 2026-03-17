package com.td.application.documents;

import com.td.application.common.models.PaginationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetDeletedDocumentsUseCase {

    private final DocumentRepository documentRepository;

    public PaginationResponse<DocumentDto> execute(SearchDocumentsRequest request) {
        try {
            Pageable pageable = buildPageable(request);
            var page = documentRepository.searchDeleted(request, pageable);

            List<DocumentDto> items = page.getContent().stream()
                .map(DocumentDtoMapper::map)
                .toList();

            return new PaginationResponse<>(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
            );
        } catch (Exception ex) {
            return new PaginationResponse<>(
                List.of(),
                Math.max(0, request.getPageNumber()),
                Math.min(Math.max(1, request.getPageSize()), 100),
                0L,
                0,
                true,
                true
            );
        }
    }

    private Pageable buildPageable(SearchDocumentsRequest request) {
        String sortBy = (request.getSortBy() == null || request.getSortBy().isBlank())
            ? "deletedOn"
            : request.getSortBy();

        Sort.Direction direction = "asc".equalsIgnoreCase(request.getSortDirection())
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;

        return PageRequest.of(
            Math.max(0, request.getPageNumber()),
            Math.min(Math.max(1, request.getPageSize()), 100),
            Sort.by(direction, sortBy)
        );
    }
}
