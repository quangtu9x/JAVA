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

// Bảng nối N:N RoleGroup ↔ AppPermission ("Nhóm vai trò" cấp quyền TRỰC TIẾP, độc lập với danh
// sách "Vai trò" con — tương đương field `roles` phía Domino, chọn qua checkbox riêng chứ không
// suy ra từ vai trò con).
@Entity
@Table(name = "role_group_permissions")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleGroupPermission extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(name = "role_group_id", nullable = false)
    private UUID roleGroupId;

    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public RoleGroupPermission(UUID roleGroupId, UUID permissionId, boolean isActive) {
        this.id = UUID.randomUUID();
        this.roleGroupId = roleGroupId;
        this.permissionId = permissionId;
        this.isActive = isActive;
    }
}
