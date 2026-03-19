package template.department.application;

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
public class SearchDepartmentsUseCase {

    private final DepartmentRepository departmentRepository;

    public PaginationResponse<DepartmentDto> execute(SearchDepartmentsRequest request) {
        try {
            Pageable pageable = buildPageable(request);
            var page = departmentRepository.search(request, pageable);

            List<DepartmentDto> items = page.getContent().stream()
                .map(DepartmentDtoMapper::map)
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

    private Pageable buildPageable(SearchDepartmentsRequest request) {
        String sortBy = (request.getSortBy() == null || request.getSortBy().isBlank())
            ? "sortOrder" : request.getSortBy();

        Sort.Direction direction = "desc".equalsIgnoreCase(request.getSortDirection())
            ? Sort.Direction.DESC : Sort.Direction.ASC;

        return PageRequest.of(
            Math.max(0, request.getPageNumber()),
            Math.min(Math.max(1, request.getPageSize()), 200),
            Sort.by(direction, sortBy)
        );
    }
}