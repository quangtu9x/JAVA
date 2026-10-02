package com.td.application.sharedcore;

import com.td.application.common.models.PaginationResponse;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.AppUserRoleGroup;
import com.td.domain.sharedcore.RoleGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(value = "identityTransactionManager", readOnly = true)
public class SearchAppUsersUseCase {

    private final AppUserRepository appUserRepository;
    private final AppUserRoleGroupRepository appUserRoleGroupRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final RoleGroupRepository roleGroupRepository;

    public PaginationResponse<AppUserDto> execute(SearchAppUsersRequest request) {
        try {
            Pageable pageable = buildPageable(request);
            var page = appUserRepository.search(request, pageable);

            // Nạp "Nhóm vai trò" cho CẢ TRANG 1 lần (tránh N+1 query cho từng user).
            List<UUID> userIds = page.getContent().stream().map(AppUser::getId).toList();
            Map<UUID, List<UUID>> roleGroupIdsByUser = appUserRoleGroupRepository
                .findByAppUserIdInAndDeletedOnIsNull(userIds).stream()
                .collect(Collectors.groupingBy(
                    AppUserRoleGroup::getAppUserId,
                    Collectors.mapping(AppUserRoleGroup::getRoleGroupId, Collectors.toList())));

            // Gap #5 (docs/analysis/18-checklist-port-kiem-nhiem-domino-sang-java.md, 08/09/2026):
            // badge "có kiêm nhiệm" ĐÚNG TOÀN CỤC — nạp 1 lần cho CẢ TRANG (tránh N+1, cùng cách
            // trên đã làm cho "Nhóm vai trò"), thay cho best-effort "cùng trang" trước đây ở
            // ItemsList.jsx (FE tự suy từ `parentUserId` của các dòng khác đang hiển thị).
            Set<UUID> idsWithKiemNhiem = userIds.isEmpty() ? Set.of() : appUserRepository
                .findByParentUserIdInAndDeletedOnIsNull(userIds).stream()
                .map(AppUser::getParentUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

            // 25/09/2026 (báo lỗi "Danh sách người dùng chưa hiển thị nhóm quyền"): tra TÊN nhóm quyền
            // 1 lần cho cả trang (tránh N+1) — trước đây DTO chỉ có roleGroupIds, cột "Nhóm quyền" ở FE
            // luôn "Chưa chọn".
            Set<UUID> allRoleGroupIds = roleGroupIdsByUser.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toCollection(HashSet::new));
            Map<UUID, String> roleGroupNameById = roleGroupRepository.findAllByIdInAndDeletedOnIsNull(allRoleGroupIds)
                .stream()
                .collect(Collectors.toMap(RoleGroup::getId, RoleGroup::getName, (a, b) -> a));

            List<AppUserDto> items = page.getContent().stream()
                .map(entity -> {
                    List<UUID> groupIds = roleGroupIdsByUser.getOrDefault(entity.getId(), List.of());
                    AppUserDto dto = AppUserDtoMapper.map(
                        entity, dynamicDocumentFieldsService.parseDynamicFields(entity.getDynamicFieldsJson()),
                        groupIds, idsWithKiemNhiem.contains(entity.getId()));
                    dto.setRoleGroupNames(groupIds.stream()
                        .map(roleGroupNameById::get)
                        .filter(Objects::nonNull)
                        .toList());
                    return dto;
                })
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

    // 24/09/2026 (yêu cầu người dùng: "danh sách người dùng ưu tiên sắp xếp theo số thứ tự, sau đó theo
    // ABC"): mặc định sortOrder TĂNG DẦN -> fullName A-Z -> username (tiêu chí cuối chỉ để phân trang ổn
    // định khi trùng cả 2). Caller truyền sortBy riêng thì cột đó làm tiêu chí CHÍNH, các tiêu chí mặc
    // định còn lại vẫn nối sau làm tiêu chí phụ (vd sortBy=sortOrder của ChuyenXuLyModalCapN trước đây
    // không có tiêu chí phụ -> người cùng số thứ tự ra thứ tự ngẫu nhiên).
    static final String SORT_ORDER = "sortOrder";
    static final String FULL_NAME = "fullName";
    static final String USERNAME = "username";
    static final String DEFAULT_SORT_BY = SearchAppUsersRequest.DEFAULT_SORT_BY;
    private static final List<String> DEFAULT_SORT_CHAIN = List.of(SORT_ORDER, FULL_NAME, USERNAME);

    private Pageable buildPageable(SearchAppUsersRequest req) {
        String sortBy = (req.getSortBy() == null || req.getSortBy().isBlank())
            ? DEFAULT_SORT_BY : req.getSortBy().trim();

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
