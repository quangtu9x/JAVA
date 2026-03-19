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
@Table(name = "app_user_data_scopes")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDataScope extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "scope_module", nullable = false, length = 100)
    private String scopeModule;

    @Column(name = "scope_type", nullable = false, length = 50)
    private String scopeType;

    @Column(name = "scope_org_id")
    private UUID scopeOrgId;

    @Column(name = "scope_value", length = 300)
    private String scopeValue;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public UserDataScope(UUID userId, String scopeModule, String scopeType,
                         UUID scopeOrgId, String scopeValue, boolean isActive) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.scopeModule = scopeModule;
        this.scopeType = scopeType;
        this.scopeOrgId = scopeOrgId;
        this.scopeValue = scopeValue;
        this.isActive = isActive;
    }

    public UserDataScope update(String scopeModule, String scopeType, UUID scopeOrgId,
                                String scopeValue, Boolean isActive) {
        if (scopeModule != null) {
            this.scopeModule = scopeModule;
        }
        if (scopeType != null) {
            this.scopeType = scopeType;
        }
        if (scopeOrgId != null) {
            this.scopeOrgId = scopeOrgId;
        }
        if (scopeValue != null) {
            this.scopeValue = scopeValue;
        }
        if (isActive != null) {
            this.isActive = isActive;
        }
        return this;
    }
}
