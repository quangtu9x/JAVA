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
@Table(name = "app_users")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(name = "keycloak_subject", nullable = false, length = 200)
    private String keycloakSubject;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "full_name", nullable = false, length = 300)
    private String fullName;

    @Column(length = 200)
    private String email;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "position_id")
    private UUID positionId;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public AppUser(String keycloakSubject, String username, String fullName, String email,
                   UUID organizationId, UUID positionId, boolean isActive) {
        this.id = UUID.randomUUID();
        this.keycloakSubject = keycloakSubject;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.organizationId = organizationId;
        this.positionId = positionId;
        this.isActive = isActive;
    }

    public AppUser update(String username, String fullName, String email,
                          UUID organizationId, UUID positionId, Boolean isActive) {
        if (username != null) {
            this.username = username;
        }
        if (fullName != null) {
            this.fullName = fullName;
        }
        if (email != null) {
            this.email = email;
        }
        if (organizationId != null) {
            this.organizationId = organizationId;
        }
        if (positionId != null) {
            this.positionId = positionId;
        }
        if (isActive != null) {
            this.isActive = isActive;
        }
        return this;
    }
}
