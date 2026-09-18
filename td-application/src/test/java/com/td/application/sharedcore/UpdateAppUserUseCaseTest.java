package com.td.application.sharedcore;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.AppUserRoleGroup;
import com.td.domain.sharedcore.RoleGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Bổ sung 19/08/2026 (bugfix — sửa lại thiết kế BL-111): "Nhóm vai trò" giờ là N:N
// (AppUserRoleGroup) — khoá lại hành vi "thay TOÀN BỘ danh sách" (giữ liên kết còn muốn, xoá mềm liên
// kết không còn được nhắc tới, thêm liên kết mới) cùng pattern UpdateRoleGroupUseCase.
@ExtendWith(MockitoExtension.class)
class UpdateAppUserUseCaseTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private RoleGroupRepository roleGroupRepository;
    @Mock
    private AppUserRoleGroupRepository appUserRoleGroupRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private UpdateAppUserUseCase newUseCase() {
        return new UpdateAppUserUseCase(appUserRepository, organizationRepository, roleGroupRepository,
            appUserRoleGroupRepository, currentUserProvider, dynamicDocumentFieldsService);
    }

    @Test
    void execute_replaceRoleGroups_shouldKeepExisting_removeUnwanted_addNew() {
        var user = new AppUser("kc-sub", "nhanvien01", "Nhân viên 1", "a@b.com", null, null, true);
        var keptGroup = new RoleGroup("Giữ lại", 0, true, true);
        var removedGroupId = UUID.randomUUID();
        var newGroup = new RoleGroup("Mới thêm", 0, true, true);

        when(appUserRepository.findByIdAndDeletedOnIsNull(user.getId())).thenReturn(Optional.of(user));
        when(roleGroupRepository.findByIdAndDeletedOnIsNull(keptGroup.getId())).thenReturn(Optional.of(keptGroup));
        when(roleGroupRepository.findByIdAndDeletedOnIsNull(newGroup.getId())).thenReturn(Optional.of(newGroup));
        when(appUserRoleGroupRepository.findByAppUserIdAndDeletedOnIsNull(user.getId())).thenReturn(List.of(
            new AppUserRoleGroup(user.getId(), keptGroup.getId(), true),
            new AppUserRoleGroup(user.getId(), removedGroupId, true)));
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());
        when(appUserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(appUserRoleGroupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // setRoleGroupIdsFromJson (KHÔNG phải setRoleGroupIds thô của Lombok) — mô phỏng đúng đường đi
        // Jackson thật, mới bật cờ updateRoleGroups=true (xem UpdateAppUserRequest.java).
        var request = new UpdateAppUserRequest();
        request.setRoleGroupIdsFromJson(List.of(keptGroup.getId(), newGroup.getId()));

        var result = newUseCase().execute(user.getId(), request);

        assertThat(result.isSuccess()).isTrue();
        var captor = org.mockito.ArgumentCaptor.forClass(AppUserRoleGroup.class);
        verify(appUserRoleGroupRepository, times(2)).save(captor.capture());
        // 1 lần xoá mềm liên kết cũ (removedGroupId) + 1 lần thêm liên kết mới (newGroup).
        assertThat(captor.getAllValues())
            .anySatisfy(row -> {
                assertThat(row.getRoleGroupId()).isEqualTo(removedGroupId);
                assertThat(row.isDeleted()).isTrue();
            })
            .anySatisfy(row -> assertThat(row.getRoleGroupId()).isEqualTo(newGroup.getId()));
    }

    @Test
    void execute_withoutUpdateRoleGroupsFlag_shouldNotTouchExistingLinks() {
        var user = new AppUser("kc-sub", "nhanvien02", "Nhân viên 2", "a@b.com", null, null, true);
        when(appUserRepository.findByIdAndDeletedOnIsNull(user.getId())).thenReturn(Optional.of(user));
        when(appUserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new UpdateAppUserRequest();
        request.setFullName("Nhân viên 2 (đổi tên)");

        var result = newUseCase().execute(user.getId(), request);

        assertThat(result.isSuccess()).isTrue();
        verify(appUserRoleGroupRepository, never()).findByAppUserIdAndDeletedOnIsNull(any());
        verify(appUserRoleGroupRepository, never()).save(any());
    }

    @Test
    void execute_withInactiveRoleGroup_shouldFail() {
        var user = new AppUser("kc-sub", "nhanvien03", "Nhân viên 3", "a@b.com", null, null, true);
        var inactiveGroup = new RoleGroup("Nhóm khoá", 0, true, false);
        when(appUserRepository.findByIdAndDeletedOnIsNull(user.getId())).thenReturn(Optional.of(user));
        when(roleGroupRepository.findByIdAndDeletedOnIsNull(inactiveGroup.getId())).thenReturn(Optional.of(inactiveGroup));

        var request = new UpdateAppUserRequest();
        request.setRoleGroupIdsFromJson(List.of(inactiveGroup.getId()));

        var result = newUseCase().execute(user.getId(), request);

        assertThat(result.isSuccess()).isFalse();
        verify(appUserRoleGroupRepository, never()).save(any());
    }
}
