package template.department.application;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DepartmentCacheStatsDto {
    private long departmentByIdHits;
    private long departmentByIdMisses;
    private long departmentByIdPuts;
    private long departmentByIdEvictions;
    private long departmentListHits;
    private long departmentListMisses;
    private long departmentListPuts;
    private long departmentListEvictions;
}