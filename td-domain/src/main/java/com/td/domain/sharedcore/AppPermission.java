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
@Table(name = "app_permissions")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppPermission extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "module_key", nullable = false, length = 100)
    private String moduleKey;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public AppPermission(String code, String name, String moduleKey, String description, boolean isActive) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.name = name;
        this.moduleKey = moduleKey;
        this.description = description;
        this.isActive = isActive;
    }

    public AppPermission update(String name, String moduleKey, String description, Boolean isActive) {
        if (name != null) {
            this.name = name;
        }
        if (moduleKey != null) {
            this.moduleKey = moduleKey;
        }
        if (description != null) {
            this.description = description;
        }
        if (isActive != null) {
            this.isActive = isActive;
        }
        return this;
    }
}
