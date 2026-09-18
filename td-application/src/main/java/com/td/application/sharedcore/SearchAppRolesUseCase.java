package com.td.application.sharedcore;

import com.td.application.common.models.PaginationResponse;
import com.td.application.documents.DynamicDocumentFieldsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(value = "identityTransactionManager", readOnly = true)
public class SearchAppRolesUseCase {

    private final AppRoleRepository appRoleRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public PaginationResponse<AppRoleDto> execute(SearchAppRolesRequest request) {
        try {
            Pageable pageable = buildPageable(request);
            var page = appRoleRepository.search(request, pageable);

            List<AppRoleDto> items = page.getContent().stream()
                .map(entity -> AppRoleDtoMapper.map(
                    entity, dynamicDocumentFieldsService.parseDynamicFields(entity.getDynamicFieldsJson())))
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
                Math.min(Math.max(1, request.getPageSize()), 200),
                0L,
                0,
                true,
                true
            );
        }
    }

    private Pageable buildPageable(SearchAppRolesRequest req) {
        String sortBy = (req.getSortBy() == null || req.getSortBy().isBlank())
            ? "name" : req.getSortBy();

        Sort.Direction direction = "desc".equalsIgnoreCase(req.getSortDirection())
            ? Sort.Direction.DESC : Sort.Direction.ASC;

        return PageRequest.of(
            Math.max(0, req.getPageNumber()),
            Math.min(Math.max(1, req.getPageSize()), 200),
            Sort.by(direction, sortBy)
        );
    }
}
