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
@Table(name = "app_user_roles")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRoleAssignment extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public UserRoleAssignment(UUID userId, UUID roleId, UUID organizationId, boolean isActive) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.roleId = roleId;
        this.organizationId = organizationId;
        this.isActive = isActive;
    }

    public UserRoleAssignment update(UUID organizationId, Boolean isActive) {
        if (organizationId != null) {
            this.organizationId = organizationId;
        }
        if (isActive != null) {
            this.isActive = isActive;
        }
        return this;
    }
}
