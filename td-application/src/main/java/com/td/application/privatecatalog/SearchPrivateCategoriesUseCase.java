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
    private final ActiveGroupMembersFilter activeGroupMembersFilter;

    public PaginationResponse<PrivateCategoryDto> execute(SearchPrivateCategoriesRequest request) {
        try {
            Pageable pageable = buildPageable(request);
            var page = privateCategoryRepository.search(request, pageable);

            List<PrivateCategoryDto> items = page.getContent().stream()
                .map(entity -> PrivateCategoryDtoMapper.map(
                    entity, dynamicDocumentFieldsService.parseDynamicFields(entity.getDynamicFieldsJson())))
                .toList();
            if (Boolean.TRUE.equals(request.getActiveMembersOnly())) {
                filterInactiveMembers(items);
            }

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

    // 06/10/2026: xem SearchPrivateCategoriesRequest#activeMembersOnly + ActiveGroupMembersFilter.
    private static final String RECEIVER_OBJ_FIELD = "receiver_obj";

    private void filterInactiveMembers(List<PrivateCategoryDto> items) {
        List<Object> filtered = activeGroupMembersFilter.filter(items.stream()
            .map(dto -> dto.getDynamicFields() == null ? null : dto.getDynamicFields().get(RECEIVER_OBJ_FIELD))
            .toList());
        for (int i = 0; i < items.size(); i++) {
            PrivateCategoryDto dto = items.get(i);
            if (dto.getDynamicFields() == null || !dto.getDynamicFields().containsKey(RECEIVER_OBJ_FIELD)) {
                continue;
            }
            java.util.Map<String, Object> fields = new java.util.LinkedHashMap<>(dto.getDynamicFields());
            fields.put(RECEIVER_OBJ_FIELD, filtered.get(i));
            dto.setDynamicFields(fields);
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
