package com.td.application.sharedcore;

import com.td.application.common.models.PaginationResponse;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.AppUserRoleGroup;
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

            List<AppUserDto> items = page.getContent().stream()
                .map(entity -> AppUserDtoMapper.map(
                    entity, dynamicDocumentFieldsService.parseDynamicFields(entity.getDynamicFieldsJson()),
                    roleGroupIdsByUser.getOrDefault(entity.getId(), List.of()),
                    idsWithKiemNhiem.contains(entity.getId())))
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

    private Pageable buildPageable(SearchAppUsersRequest req) {
        String sortBy = (req.getSortBy() == null || req.getSortBy().isBlank())
            ? "fullName" : req.getSortBy();

        Sort.Direction direction = "desc".equalsIgnoreCase(req.getSortDirection())
            ? Sort.Direction.DESC : Sort.Direction.ASC;

        return PageRequest.of(
            Math.max(0, req.getPageNumber()),
            Math.min(Math.max(1, req.getPageSize()), 200),
            Sort.by(direction, sortBy)
        );
    }
}
