package com.td.application.sharedcore;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.sharedcore.RoleGroupRole;

import java.util.List;
import java.util.UUID;

// Bảng nối N:N RoleGroup ↔ AppRole — chỉ cần đọc theo roleGroupId (đơn/nhiều) để phục vụ
// Get/SearchRoleGroupsUseCase và ghi/xoá theo transaction "thay toàn bộ danh sách" trong
// Create/UpdateRoleGroupUseCase (không cần thêm method tìm kiếm phức tạp).
public interface RoleGroupRoleRepository extends IRepository<RoleGroupRole> {

    List<RoleGroupRole> findByRoleGroupIdAndDeletedOnIsNull(UUID roleGroupId);

    List<RoleGroupRole> findByRoleGroupIdInAndDeletedOnIsNull(List<UUID> roleGroupIds);
}
