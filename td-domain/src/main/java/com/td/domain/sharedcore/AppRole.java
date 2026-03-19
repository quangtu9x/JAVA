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
@Table(name = "app_roles")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppRole extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_system_role", nullable = false)
    private boolean isSystemRole = false;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public AppRole(String code, String name, String description, boolean isSystemRole, boolean isActive) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.name = name;
        this.description = description;
        this.isSystemRole = isSystemRole;
        this.isActive = isActive;
    }

    public AppRole update(String name, String description, Boolean isSystemRole, Boolean isActive) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (isSystemRole != null) {
            this.isSystemRole = isSystemRole;
        }
        if (isActive != null) {
            this.isActive = isActive;
        }
        return this;
    }
}
