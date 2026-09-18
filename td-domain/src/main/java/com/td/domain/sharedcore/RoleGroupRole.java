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

// Bảng nối N:N RoleGroup ↔ AppRole ("Nhóm vai trò" chứa nhiều "Vai trò" con — tương đương
// list_role_obj phía Domino). Cùng convention với RolePermission (app_role_permissions).
@Entity
@Table(name = "role_group_roles")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleGroupRole extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(name = "role_group_id", nullable = false)
    private UUID roleGroupId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public RoleGroupRole(UUID roleGroupId, UUID roleId, boolean isActive) {
        this.id = UUID.randomUUID();
        this.roleGroupId = roleGroupId;
        this.roleId = roleId;
        this.isActive = isActive;
    }
}
