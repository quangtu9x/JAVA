package com.td.application.sharedcore;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Bổ sung 19/08/2026 (bugfix — sửa lại thiết kế BL-111): "Nhóm vai trò" giờ là N:N
// (AppUserRoleGroup), 1 user có thể được gán NHIỀU nhóm cùng lúc lúc tạo mới.
@ExtendWith(MockitoExtension.class)
class CreateAppUserUseCaseTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private RoleGroupRepository roleGroupRepository;
    @Mock
    private AppUserRoleGroupRepository appUserRoleGroupRepository;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private CreateAppUserUseCase newUseCase() {
        return new CreateAppUserUseCase(appUserRepository, organizationRepository, roleGroupRepository,
            appUserRoleGroupRepository, dynamicDocumentFieldsService);
    }

    private CreateAppUserRequest validRequest() {
        var request = new CreateAppUserRequest();
        request.setUsername("nhanvien01");
        request.setFullName("Nhân viên 1");
        return request;
    }

    @Test
    void execute_withMultipleRoleGroups_shouldCreateOneLinkPerGroup() {
        var group1 = new RoleGroup("Nhóm 1", 0, true, true);
        var group2 = new RoleGroup("Nhóm 2", 0, true, true);
        when(appUserRepository.existsByUsernameAndDeletedOnIsNull("nhanvien01")).thenReturn(false);
        when(roleGroupRepository.findByIdAndDeletedOnIsNull(group1.getId())).thenReturn(Optional.of(group1));
        when(roleGroupRepository.findByIdAndDeletedOnIsNull(group2.getId())).thenReturn(Optional.of(group2));
        when(appUserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = validRequest();
        request.setRoleGroupIds(List.of(group1.getId(), group2.getId()));

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(appUserRoleGroupRepository, times(2)).save(any(AppUserRoleGroup.class));
    }

    @Test
    void execute_withInactiveRoleGroup_shouldFail() {
        var inactiveGroup = new RoleGroup("Nhóm khoá", 0, true, false);
        when(appUserRepository.existsByUsernameAndDeletedOnIsNull("nhanvien01")).thenReturn(false);
        when(roleGroupRepository.findByIdAndDeletedOnIsNull(inactiveGroup.getId())).thenReturn(Optional.of(inactiveGroup));

        var request = validRequest();
        request.setRoleGroupIds(List.of(inactiveGroup.getId()));

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(appUserRepository, never()).save(any());
        verify(appUserRoleGroupRepository, never()).save(any());
    }

    @Test
    void execute_withUnknownRoleGroup_shouldFail() {
        var unknownId = UUID.randomUUID();
        when(appUserRepository.existsByUsernameAndDeletedOnIsNull("nhanvien01")).thenReturn(false);
        when(roleGroupRepository.findByIdAndDeletedOnIsNull(unknownId)).thenReturn(Optional.empty());

        var request = validRequest();
        request.setRoleGroupIds(List.of(unknownId));

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void execute_withoutRoleGroups_shouldStillCreateUser() {
        when(appUserRepository.existsByUsernameAndDeletedOnIsNull("nhanvien01")).thenReturn(false);
        when(appUserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = newUseCase().execute(validRequest());

        assertThat(result.isSuccess()).isTrue();
        verify(appUserRoleGroupRepository, never()).save(any());
    }

    // Gap #2 (docs/analysis/18-checklist-port-kiem-nhiem-domino-sang-java.md, 08/09/2026): validate
    // parentUserId (kiêm nhiệm) — trước đây không kiểm tra gì.
    @Test
    void execute_withUnknownParentUserId_shouldFail() {
        var unknownParentId = UUID.randomUUID();
        when(appUserRepository.existsByUsernameAndDeletedOnIsNull("nhanvien01")).thenReturn(false);
        when(appUserRepository.findByIdAndDeletedOnIsNull(unknownParentId)).thenReturn(Optional.empty());

        var request = validRequest();
        request.setParentUserId(unknownParentId);

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void execute_withParentThatIsItselfKiemNhiem_shouldFail() {
        var grandParentId = UUID.randomUUID();
        var parent = new AppUser("kc-parent01", "parent01", "Parent", null, null, null, true,
            null, 0, null, null, null, null, null, null, null, false, grandParentId);
        when(appUserRepository.existsByUsernameAndDeletedOnIsNull("nhanvien01")).thenReturn(false);
        when(appUserRepository.findByIdAndDeletedOnIsNull(parent.getId())).thenReturn(Optional.of(parent));

        var request = validRequest();
        request.setParentUserId(parent.getId());

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void execute_withValidParentUserId_shouldCreateKiemNhiem() {
        var parent = new AppUser("kc-parent02", "parent02", "Parent 2", null, null, null, true,
            null, 0, null, null, null, null, null, null, null, false, null);
        when(appUserRepository.existsByUsernameAndDeletedOnIsNull("nhanvien01")).thenReturn(false);
        when(appUserRepository.findByIdAndDeletedOnIsNull(parent.getId())).thenReturn(Optional.of(parent));
        when(appUserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = validRequest();
        request.setParentUserId(parent.getId());

        var result = newUseCase().execute(request);

        assertThat(result.isSuccess()).isTrue();
    }
}
