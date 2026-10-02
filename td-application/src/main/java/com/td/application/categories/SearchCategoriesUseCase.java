package com.td.application.categories;

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
public class SearchCategoriesUseCase {

    private final CategoryRepository categoryRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public PaginationResponse<CategoryDto> execute(SearchCategoriesRequest request) {
        try {
            Pageable pageable = buildPageable(request);
            var page = categoryRepository.search(request, pageable);

            List<CategoryDto> items = page.getContent().stream()
                .map(entity -> CategoryDtoMapper.map(
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

    // 24/09/2026 (yêu cầu người dùng, cùng quy ước "Người dùng" - SearchAppUsersUseCase): ưu tiên
    // "Thứ tự hiển thị" tăng dần, sau đó Tên A-Z, cuối cùng Mã (chỉ để phân trang ổn định khi trùng cả 2).
    // sortBy caller truyền vào làm tiêu chí CHÍNH, các tiêu chí mặc định còn lại nối sau làm tiêu chí phụ.
    private static final List<String> DEFAULT_SORT_CHAIN = List.of("sortOrder", "name", "code");

    private Pageable buildPageable(SearchCategoriesRequest req) {
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
