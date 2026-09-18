package com.td.domain.sharedcore;

import com.td.domain.common.contracts.AuditableEntity;
import com.td.domain.common.contracts.IAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// Bổ sung 19/08/2026 (bugfix — sửa lại thiết kế BL-111): bảng nối N:N AppUser <-> RoleGroup — thay
// thế FK đơn `app_users.role_group_id` cũ. Theo phản hồi trực tiếp "1 user = nhiều role group"
// (khác giả định ban đầu "Domino chỉ gán 1 nhóm/user" — đã xác nhận lại là SAI, cần đúng N:N). Cùng
// convention RoleGroupRole/RoleGroupPermission (bảng nối N:N khác của RoleGroup).
@Entity
@Table(name = "app_user_role_groups")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUserRoleGroup extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(name = "app_user_id", nullable = false)
    private UUID appUserId;

    @Column(name = "role_group_id", nullable = false)
    private UUID roleGroupId;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public AppUserRoleGroup(UUID appUserId, UUID roleGroupId, boolean isActive) {
        this.id = UUID.randomUUID();
        this.appUserId = appUserId;
        this.roleGroupId = roleGroupId;
        this.isActive = isActive;
    }
}
