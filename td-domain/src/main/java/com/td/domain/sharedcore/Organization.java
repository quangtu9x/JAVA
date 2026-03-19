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
@Table(name = "organizations")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organization extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "level", nullable = false)
    private int level = 0;

    @Column(name = "full_path", nullable = false, columnDefinition = "TEXT")
    private String fullPath;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public Organization(String code, String name, String description, UUID parentId, int level, String fullPath, int sortOrder) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.name = name;
        this.description = description;
        this.parentId = parentId;
        this.level = level;
        this.fullPath = fullPath;
        this.sortOrder = sortOrder;
        this.isActive = true;
    }

    public Organization update(String code, String name, String description,
                               UUID parentId, int level, String fullPath,
                               Integer sortOrder, Boolean isActive) {
        if (code != null) {
            this.code = code;
        }
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        this.parentId = parentId;
        this.level = level;
        this.fullPath = fullPath;
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
        if (isActive != null) {
            this.isActive = isActive;
        }
        return this;
    }
}
