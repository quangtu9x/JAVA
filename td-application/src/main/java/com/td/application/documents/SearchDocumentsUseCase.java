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
public class SearchDocumentsUseCase {

    private final DocumentRepository documentRepository;

    public PaginationResponse<DocumentDto> execute(SearchDocumentsRequest request) {
        try {
            Pageable pageable = buildPageable(request);
            var page = documentRepository.search(request, pageable);

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

    private DocumentDto mapToDto(BusinessDocument document) {
        var dto = new DocumentDto();
        dto.setId(document.getId());
        dto.setTitle(document.getTitle());
        dto.setDocumentType(document.getDocumentType());
        dto.setStatus(document.getStatus());
        dto.setContent(document.getContent());
        dto.setTags(DocumentJsonMapper.toStringList(document.getTagsJson()));
        dto.setAttributes(DocumentJsonMapper.toMap(document.getAttributesJson()));
        dto.setMetadata(DocumentJsonMapper.toMap(document.getMetadataJson()));
        dto.setVersionNo(document.getVersionNo());
        dto.setCreatedOn(document.getCreatedOn());
        dto.setLastModifiedOn(document.getLastModifiedOn());
        dto.setDeleted(document.isDeleted());
        return dto;
    }
}
