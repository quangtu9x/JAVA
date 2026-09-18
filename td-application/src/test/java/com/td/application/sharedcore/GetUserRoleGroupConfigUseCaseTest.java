package com.td.application.sharedcore;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppPermission;
import com.td.domain.sharedcore.AppRole;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.AppUserRoleGroup;
import com.td.domain.sharedcore.RoleGroupPermission;
import com.td.domain.sharedcore.RoleGroupRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// Bổ sung 19/08/2026 (bugfix): "Nhóm vai trò" (RoleGroup) chưa được GET /qlvb/profile tra cứu, khiến
// user chỉ được cấp quyền/menu qua đây không thấy được menu chức năng. Test khoá lại use case tra cứu,
// bao gồm cả trường hợp 1 user thuộc NHIỀU role group (N:N, xem AppUserRoleGroup.java — sửa lại giả
// định ban đầu "chỉ 1 nhóm/user" cho đúng thực tế).
@ExtendWith(MockitoExtension.class)
class GetUserRoleGroupConfigUseCaseTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private AppUserRoleGroupRepository appUserRoleGroupRepository;
    @Mock
    private RoleGroupRoleRepository roleGroupRoleRepository;
    @Mock
    private RoleGroupPermissionRepository roleGroupPermissionRepository;
    @Mock
    private AppRoleRepository appRoleRepository;
    @Mock
    private AppPermissionRepository appPermissionRepository;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private GetUserRoleGroupConfigUseCase newUseCase() {
        return new GetUserRoleGroupConfigUseCase(appUserRepository, appUserRoleGroupRepository,
            roleGroupRoleRepository, roleGroupPermissionRepository, appRoleRepository, appPermissionRepository,
            dynamicDocumentFieldsService);
    }

    @Test
    void execute_userNotFound_shouldReturnEmptyConfig() {
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("khongtontai")).thenReturn(Optional.empty());

        var result = newUseCase().execute("khongtontai");

        assertThat(result.getPermissionCodes()).isEmpty();
        assertThat(result.getMenuFields()).isEmpty();
    }

    @Test
    void execute_userWithoutRoleGroup_shouldReturnEmptyConfig() {
        var user = new AppUser("kc-sub", "nhanvien01", "Nhân viên 1", "a@b.com", null, null, true);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nhanvien01")).thenReturn(Optional.of(user));
        when(appUserRoleGroupRepository.findByAppUserIdAndDeletedOnIsNull(user.getId())).thenReturn(List.of());

        var result = newUseCase().execute("nhanvien01");

        assertThat(result.getPermissionCodes()).isEmpty();
        assertThat(result.getMenuFields()).isEmpty();
    }

    @Test
    void execute_userWithOneRoleGroup_shouldMergePermissionsAndMenusAcrossMultipleRoles() {
        var roleGroupId = UUID.randomUUID();
        var user = new AppUser("kc-sub", "nhanvien02", "Nhân viên 2", "a@b.com", null, null, true);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nhanvien02")).thenReturn(Optional.of(user));
        when(appUserRoleGroupRepository.findByAppUserIdAndDeletedOnIsNull(user.getId()))
            .thenReturn(List.of(new AppUserRoleGroup(user.getId(), roleGroupId, true)));

        var permission = new AppPermission("QCapNhatVBDen", "Cập nhật VB đến", "vbden", "", true);
        when(roleGroupPermissionRepository.findByRoleGroupIdInAndDeletedOnIsNull(List.of(roleGroupId)))
            .thenReturn(List.of(new RoleGroupPermission(roleGroupId, permission.getId(), true)));
        when(appPermissionRepository.findByIdInAndDeletedOnIsNull(List.of(permission.getId())))
            .thenReturn(List.of(permission));

        // "menu_top" là chuỗi nối ";", còn "menu_qlvb" (và mọi menu_* khác) là chuỗi ĐÃ JSON-encode
        // sẵn 1 mảng (xem javadoc GetUserRoleGroupConfigUseCase — 2 định dạng KHÁC nhau, cố tình viết
        // test cho cả 2 để khoá lại, tránh tái diễn bug parse nhầm split(";") lên menu_qlvb).
        var role1 = new AppRole("VBDEN_STAFF", "Chuyên viên VB đến", "", false, true, 0);
        role1.replaceDynamicFields("{\"menu_top\":\"Văn bản đến;\",\"menu_qlvb\":\"[\\\"Người dùng\\\"]\"}");
        var role2 = new AppRole("VBDI_STAFF", "Chuyên viên VB đi", "", false, true, 0);
        role2.replaceDynamicFields("{\"menu_top\":\"Văn bản đến;Văn bản đi;\",\"menu_qlvb\":\"[\\\"Vai trò\\\"]\"}");
        when(roleGroupRoleRepository.findByRoleGroupIdInAndDeletedOnIsNull(List.of(roleGroupId))).thenReturn(List.of(
            new RoleGroupRole(roleGroupId, role1.getId(), true),
            new RoleGroupRole(roleGroupId, role2.getId(), true)));
        when(appRoleRepository.findByIdInAndDeletedOnIsNull(List.of(role1.getId(), role2.getId())))
            .thenReturn(List.of(role1, role2));

        var result = newUseCase().execute("nhanvien02");

        assertThat(result.getPermissionCodes()).containsExactly("QCapNhatVBDen");
        // Hợp nhất (union, khử trùng, giữ thứ tự) từ CẢ 2 vai trò con — "Văn bản đến" chỉ xuất hiện 1 lần.
        assertThat(result.getMenuFields().get("menu_top")).containsExactly("Văn bản đến", "Văn bản đi");
        assertThat(result.getMenuFields().get("menu_qlvb")).containsExactly("Người dùng", "Vai trò");
    }

    // Trọng tâm bugfix: user thuộc 2 "Nhóm vai trò" KHÁC NHAU (không phải 2 vai trò con của 1 nhóm) —
    // quyền/menu phải hợp nhất qua CẢ 2 nhóm.
    @Test
    void execute_userWithMultipleRoleGroups_shouldMergePermissionsAcrossGroups() {
        var groupId1 = UUID.randomUUID();
        var groupId2 = UUID.randomUUID();
        var user = new AppUser("kc-sub", "nhanvien03", "Nhân viên 3", "a@b.com", null, null, true);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nhanvien03")).thenReturn(Optional.of(user));
        when(appUserRoleGroupRepository.findByAppUserIdAndDeletedOnIsNull(user.getId())).thenReturn(List.of(
            new AppUserRoleGroup(user.getId(), groupId1, true),
            new AppUserRoleGroup(user.getId(), groupId2, true)));

        var permission1 = new AppPermission("QCapNhatVBDen", "Cập nhật VB đến", "vbden", "", true);
        var permission2 = new AppPermission("QCapNhatVBDi", "Cập nhật VB đi", "vbdi", "", true);
        when(roleGroupPermissionRepository.findByRoleGroupIdInAndDeletedOnIsNull(List.of(groupId1, groupId2)))
            .thenReturn(List.of(
                new RoleGroupPermission(groupId1, permission1.getId(), true),
                new RoleGroupPermission(groupId2, permission2.getId(), true)));
        when(appPermissionRepository.findByIdInAndDeletedOnIsNull(List.of(permission1.getId(), permission2.getId())))
            .thenReturn(List.of(permission1, permission2));
        when(roleGroupRoleRepository.findByRoleGroupIdInAndDeletedOnIsNull(List.of(groupId1, groupId2)))
            .thenReturn(List.of());
        when(appRoleRepository.findByIdInAndDeletedOnIsNull(List.of())).thenReturn(List.of());

        var result = newUseCase().execute("nhanvien03");

        assertThat(result.getPermissionCodes()).containsExactlyInAnyOrder("QCapNhatVBDen", "QCapNhatVBDi");
    }
}
