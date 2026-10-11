package com.td.application.sharedcore;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Gap #5 (docs/analysis/18-checklist-port-kiem-nhiem-domino-sang-java.md, 08/09/2026): badge "có
// kiêm nhiệm" đúng toàn cục — nạp 1 lần cho CẢ TRANG (tránh N+1), thay best-effort trong-trang cũ.
@ExtendWith(MockitoExtension.class)
class SearchAppUsersUseCaseTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private AppUserRoleGroupRepository appUserRoleGroupRepository;
    @Mock
    private DynamicDocumentFieldsService dynamicDocumentFieldsService;
    @Mock
    private RoleGroupRepository roleGroupRepository;

    private SearchAppUsersUseCase newUseCase() {
        return new SearchAppUsersUseCase(appUserRepository, appUserRoleGroupRepository, dynamicDocumentFieldsService,
            roleGroupRepository, UserDelegationTestSupport.emptyResolver());
    }

    @Test
    void execute_pageWithSomeParents_marksOnlyParentsAsHasKiemNhiem() {
        var parent = new AppUser("kc-cha", "cha01", "Cha", null, null, null, true);
        var soloUser = new AppUser("kc-doclap", "doclap01", "Độc lập", null, null, null, true);
        Page<AppUser> page = new PageImpl<>(List.of(parent, soloUser));

        when(appUserRepository.search(any(), any(Pageable.class))).thenReturn(page);
        when(appUserRoleGroupRepository.findByAppUserIdInAndDeletedOnIsNull(any())).thenReturn(List.of());
        when(dynamicDocumentFieldsService.parseDynamicFields(anyString())).thenReturn(Map.of());
        // Đúng 1 con trỏ parentUserId về "parent" — soloUser không có con nào.
        var child = new AppUser("kc-con", "con01", "Con", null, null, null, true,
            null, 0, null, null, null, null, null, null, null, false, parent.getId());
        when(appUserRepository.findByParentUserIdInAndDeletedOnIsNull(List.of(parent.getId(), soloUser.getId())))
            .thenReturn(List.of(child));

        var result = newUseCase().execute(new SearchAppUsersRequest());

        assertThat(result.getItems()).hasSize(2);
        var parentDto = result.getItems().stream().filter(dto -> dto.getId().equals(parent.getId())).findFirst().orElseThrow();
        var soloDto = result.getItems().stream().filter(dto -> dto.getId().equals(soloUser.getId())).findFirst().orElseThrow();
        assertThat(parentDto.isHasKiemNhiem()).isTrue();
        assertThat(soloDto.isHasKiemNhiem()).isFalse();
    }

    @Test
    void execute_emptyPage_doesNotQueryKiemNhiem() {
        Page<AppUser> page = new PageImpl<>(List.of());
        when(appUserRepository.search(any(), any(Pageable.class))).thenReturn(page);
        when(appUserRoleGroupRepository.findByAppUserIdInAndDeletedOnIsNull(any())).thenReturn(List.of());

        var result = newUseCase().execute(new SearchAppUsersRequest());

        assertThat(result.getItems()).isEmpty();
        verify(appUserRepository, never()).findByParentUserIdInAndDeletedOnIsNull(any());
    }

    // 24/09/2026 (yêu cầu người dùng): mặc định ưu tiên "Thứ tự hiển thị" tăng dần, sau đó Họ tên A-Z,
    // cuối cùng Tên đăng nhập (ổn định phân trang).
    @Test
    void execute_defaultSort_isSortOrderThenFullNameThenUsername() {
        when(appUserRepository.search(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(appUserRoleGroupRepository.findByAppUserIdInAndDeletedOnIsNull(any())).thenReturn(List.of());

        newUseCase().execute(new SearchAppUsersRequest());

        assertThat(capturedSort()).containsExactly(
            Sort.Order.asc("sortOrder"), Sort.Order.asc("fullName"), Sort.Order.asc("username"));
    }

    @Test
    void execute_explicitSortBy_keepsItPrimaryAndAppendsRemainingDefaults() {
        when(appUserRepository.search(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(appUserRoleGroupRepository.findByAppUserIdInAndDeletedOnIsNull(any())).thenReturn(List.of());
        var request = new SearchAppUsersRequest();
        request.setSortBy("fullName");
        request.setSortDirection("desc");

        newUseCase().execute(request);

        assertThat(capturedSort()).containsExactly(
            Sort.Order.desc("fullName"), Sort.Order.asc("sortOrder"), Sort.Order.asc("username"));
    }

    // 25/09/2026 (báo lỗi "Danh sách người dùng chưa hiển thị nhóm quyền"): DTO trả kèm TÊN nhóm quyền,
    // đúng thứ tự roleGroupIds, bỏ qua nhóm không còn tồn tại (đã xoá).
    @Test
    void execute_fillsRoleGroupNames_inRoleGroupIdOrder_skippingDeletedGroups() {
        var user = new AppUser("kc-1", "user01", "Nguoi dung 1", null, null, null, true);
        var groupA = new com.td.domain.sharedcore.RoleGroup("Văn thư", 1, false, true);
        var groupB = new com.td.domain.sharedcore.RoleGroup("Lãnh đạo", 2, false, true);
        java.util.UUID deletedGroupId = java.util.UUID.randomUUID();
        when(appUserRepository.search(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(user)));
        when(appUserRoleGroupRepository.findByAppUserIdInAndDeletedOnIsNull(any())).thenReturn(List.of(
            new com.td.domain.sharedcore.AppUserRoleGroup(user.getId(), groupB.getId(), true),
            new com.td.domain.sharedcore.AppUserRoleGroup(user.getId(), deletedGroupId, true),
            new com.td.domain.sharedcore.AppUserRoleGroup(user.getId(), groupA.getId(), true)));
        when(roleGroupRepository.findAllByIdInAndDeletedOnIsNull(any())).thenReturn(List.of(groupA, groupB));
        when(dynamicDocumentFieldsService.parseDynamicFields(anyString())).thenReturn(Map.of());

        var result = newUseCase().execute(new SearchAppUsersRequest());

        assertThat(result.getItems().get(0).getRoleGroupNames()).containsExactly("Lãnh đạo", "Văn thư");
    }

    private List<Sort.Order> capturedSort() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(appUserRepository).search(any(), captor.capture());
        return captor.getValue().getSort().toList();
    }
}
