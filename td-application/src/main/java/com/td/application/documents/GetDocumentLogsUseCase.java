package com.td.application.documents;

import com.td.application.common.models.PaginationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * BL-103 (Audit Log) — DoD (5): API tra cứu log theo documentId.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetDocumentLogsUseCase {

    private final DocumentLogRepository documentLogRepository;

    public PaginationResponse<DocumentLogDto> execute(UUID documentId, int pageNumber, int pageSize) {
        try {
            Pageable pageable = buildPageable(pageNumber, pageSize);
            var page = documentLogRepository.findByDocumentId(documentId, pageable);

            List<DocumentLogDto> items = page.getContent().stream()
                .map(DocumentLogDtoMapper::map)
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
                Math.max(0, pageNumber),
                Math.min(Math.max(1, pageSize), 200),
                0L,
                0,
                true,
                true
            );
        }
    }

    private Pageable buildPageable(int pageNumber, int pageSize) {
        int normalizedPageNumber = Math.max(0, pageNumber);
        int normalizedPageSize = Math.min(Math.max(1, pageSize), 200);
        return PageRequest.of(normalizedPageNumber, normalizedPageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
