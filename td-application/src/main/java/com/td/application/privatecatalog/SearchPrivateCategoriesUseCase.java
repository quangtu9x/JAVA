package com.td.application.privatecatalog;

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
@Transactional(readOnly = true)
public class SearchPrivateCategoriesUseCase {

    private final PrivateCategoryRepository privateCategoryRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public PaginationResponse<PrivateCategoryDto> execute(SearchPrivateCategoriesRequest request) {
        try {
            Pageable pageable = buildPageable(request);
            var page = privateCategoryRepository.search(request, pageable);

            List<PrivateCategoryDto> items = page.getContent().stream()
                .map(entity -> PrivateCategoryDtoMapper.map(
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
                0L, 0, true, true
            );
        }
    }

    // 25/09/2026 (quy ước "Thứ tự hiển thị", cùng SearchCategoriesUseCase/SearchAppUsersUseCase): ưu tiên
    // sortOrder tăng dần, sau đó Tên A-Z, cuối cùng Mã (ổn định phân trang). sortBy caller truyền vào làm
    // tiêu chí CHÍNH, các tiêu chí mặc định còn lại nối sau.
    private static final List<String> DEFAULT_SORT_CHAIN = List.of("sortOrder", "name", "code");

    private Pageable buildPageable(SearchPrivateCategoriesRequest req) {
        String sortBy = (req.getSortBy() == null || req.getSortBy().isBlank())
            ? "sortOrder" : req.getSortBy().trim();

        Sort.Direction direction = "desc".equalsIgnoreCase(req.getSortDirection())
            ? Sort.Direction.DESC : Sort.Direction.ASC;

        Sort sort = Sort.by(direction, sortBy);
        for (String secondary : DEFAULT_SORT_CHAIN) {
            if (!secondary.equals(sortBy)) {
                sort = sort.and(Sort.by(Sort.Direction.ASC, secondary));
            }
        }

        return PageRequest.of(
            Math.max(0, req.getPageNumber()),
            Math.min(Math.max(1, req.getPageSize()), 200),
            sort
        );
    }
}
