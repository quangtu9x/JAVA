package com.td.application.sharedcore;

import com.td.application.common.models.PaginationResponse;
import com.td.domain.sharedcore.AppRole;
import com.td.domain.sharedcore.RoleGroup;
import com.td.domain.sharedcore.RoleGroupRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(value = "identityTransactionManager", readOnly = true)
public class SearchRoleGroupsUseCase {

    private final RoleGroupRepository roleGroupRepository;
    private final RoleGroupRoleRepository roleGroupRoleRepository;
    private final AppRoleRepository appRoleRepository;

    public PaginationResponse<RoleGroupDto> execute(SearchRoleGroupsRequest request) {
        try {
            Pageable pageable = buildPageable(request);
            var page = roleGroupRepository.search(request, pageable);
            List<RoleGroup> roleGroups = page.getContent();

            // Nạp toàn bộ liên kết "vai trò" con của cả trang bằng 2 truy vấn (không N+1) — danh
            // sách "quyền" trực tiếp KHÔNG cần cho màn danh sách (FE chỉ hiển thị cột "Danh sách
            // vai trò", xem nhomvaitro/components/ItemsList.jsx).
            List<java.util.UUID> roleGroupIds = roleGroups.stream().map(RoleGroup::getId).toList();
            List<RoleGroupRole> allLinks = roleGroupIds.isEmpty()
                ? List.of() : roleGroupRoleRepository.findByRoleGroupIdInAndDeletedOnIsNull(roleGroupIds);

            List<java.util.UUID> allRoleIds = allLinks.stream().map(RoleGroupRole::getRoleId).distinct().toList();
            Map<java.util.UUID, AppRole> rolesById = allRoleIds.isEmpty()
                ? Map.of()
                : appRoleRepository.findByIdInAndDeletedOnIsNull(allRoleIds).stream()
                    .collect(Collectors.toMap(AppRole::getId, r -> r));

            Map<java.util.UUID, List<AppRole>> rolesByGroupId = allLinks.stream()
                .collect(Collectors.groupingBy(
                    RoleGroupRole::getRoleGroupId,
                    Collectors.mapping(link -> rolesById.get(link.getRoleId()), Collectors.toList())
                ));

            List<RoleGroupDto> items = roleGroups.stream()
                .map(rg -> RoleGroupDtoMapper.map(
                    rg,
                    rolesByGroupId.getOrDefault(rg.getId(), List.of()).stream().filter(java.util.Objects::nonNull).toList(),
                    List.of()
                ))
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

    private Pageable buildPageable(SearchRoleGroupsRequest req) {
        String sortBy = (req.getSortBy() == null || req.getSortBy().isBlank())
            ? "sortOrder" : req.getSortBy();

        Sort.Direction direction = "desc".equalsIgnoreCase(req.getSortDirection())
            ? Sort.Direction.DESC : Sort.Direction.ASC;

        return PageRequest.of(
            Math.max(0, req.getPageNumber()),
            Math.min(Math.max(1, req.getPageSize()), 200),
            Sort.by(direction, sortBy)
        );
    }
}
