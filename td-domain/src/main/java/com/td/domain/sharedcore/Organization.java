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
    private String identifier;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "form", nullable = false, length = 30)
    private String nodeType = "agency_level";

    @Column(name = "level", nullable = false)
    private int level = 0;

    @Column(name = "full_path", nullable = false, columnDefinition = "TEXT")
    private String fullPath;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "system", nullable = false)
    private int system = 0;

    @Column(name = "receiver_id", length = 100)
    private String receiverId;

    @Column(name = "receiver", length = 200)
    private String receiver;

    @Column(name = "receiver_position", length = 200)
    private String receiverPosition;

    @Column(name = "parent", length = 300)
    private String parent;

    @Column(name = "parentid", length = 64)
    private String legacyParentId;

    @Column(name = "servername", length = 300)
    private String servername;

    @Column(name = "server_id", length = 64)
    private String serverId;

    @Column(name = "ipserver", length = 100)
    private String ipserver;

    @Column(name = "dbpath", length = 100)
    private String dbpath;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public Organization(String identifier, String name, String description, UUID parentId,
                        String nodeType, int level, String fullPath, int sortOrder,
                        Integer system, String receiverId, String receiver,
                        String receiverPosition, String parent, String legacyParentId,
                        String servername, String serverId, String ipserver, String dbpath) {
        this.id = UUID.randomUUID();
        this.identifier = identifier;
        this.name = name;
        this.description = description;
        this.parentId = parentId;
        this.nodeType = nodeType;
        this.level = level;
        this.fullPath = fullPath;
        this.sortOrder = sortOrder;
        this.system = system == null ? 0 : system;
        this.receiverId = receiverId;
        this.receiver = receiver;
        this.receiverPosition = receiverPosition;
        this.parent = parent;
        this.legacyParentId = legacyParentId;
        this.servername = servername;
        this.serverId = serverId;
        this.ipserver = ipserver;
        this.dbpath = dbpath;
        this.isActive = true;
    }

    public Organization update(String identifier, String name, String description,
                               UUID parentId, String nodeType, int level, String fullPath,
                               Integer sortOrder, Boolean isActive) {
        if (identifier != null) {
            this.identifier = identifier;
        }
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        this.parentId = parentId;
        if (nodeType != null) {
            this.nodeType = nodeType;
        }
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

    public Organization updateLegacyData(Integer system, String receiverId, String receiver,
                                         String receiverPosition, String parent, String legacyParentId,
                                         String servername, String serverId, String ipserver,
                                         String dbpath) {
        if (system != null) {
            this.system = system;
        }
        if (receiverId != null) {
            this.receiverId = receiverId;
        }
        if (receiver != null) {
            this.receiver = receiver;
        }
        if (receiverPosition != null) {
            this.receiverPosition = receiverPosition;
        }
        if (parent != null) {
            this.parent = parent;
        }
        if (legacyParentId != null) {
            this.legacyParentId = legacyParentId;
        }
        if (servername != null) {
            this.servername = servername;
        }
        if (serverId != null) {
            this.serverId = serverId;
        }
        if (ipserver != null) {
            this.ipserver = ipserver;
        }
        if (dbpath != null) {
            this.dbpath = dbpath;
        }
        return this;
    }
}
