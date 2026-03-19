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

@Entity
@Table(name = "app_role_permissions")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RolePermission extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public RolePermission(UUID roleId, UUID permissionId, boolean isActive) {
        this.id = UUID.randomUUID();
        this.roleId = roleId;
        this.permissionId = permissionId;
        this.isActive = isActive;
    }

    public RolePermission update(Boolean isActive) {
        if (isActive != null) {
            this.isActive = isActive;
        }
        return this;
    }
}
