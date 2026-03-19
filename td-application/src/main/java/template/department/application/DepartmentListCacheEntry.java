package template.department.application;

import com.td.application.common.models.PaginationResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentListCacheEntry {

    private List<DepartmentDto> items = Collections.emptyList();
    private int pageNumber;
    private int pageSize;
    private long totalItems;
    private int totalPages;
    private boolean first;
    private boolean last;

    public static DepartmentListCacheEntry from(PaginationResponse<DepartmentDto> response) {
        if (response == null) {
            return null;
        }
        return DepartmentListCacheEntry.builder()
            .items(response.getItems() == null ? Collections.emptyList() : response.getItems())
            .pageNumber(response.getPageNumber())
            .pageSize(response.getPageSize())
            .totalItems(response.getTotalItems())
            .totalPages(response.getTotalPages())
            .first(response.isFirst())
            .last(response.isLast())
            .build();
    }

    public PaginationResponse<DepartmentDto> toPaginationResponse() {
        return new PaginationResponse<>(
            items == null ? Collections.emptyList() : items,
            pageNumber,
            pageSize,
            totalItems,
            totalPages,
            first,
            last
        );
    }
}