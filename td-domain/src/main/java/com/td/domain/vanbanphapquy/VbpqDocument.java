package com.td.domain.vanbanphapquy;

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

/**
 * 30/10/2026 (port module "Văn bản pháp quy" - Domino vanbanphapquy.nsf): 1 văn bản pháp quy của 1 cơ quan
 * ({@code coquanId}/{@code coquanIdentifier}); field nghiệp vụ trong {@code dynamic_fields}.
 */
@Entity
@Table(name = "vbpq_documents")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VbpqDocument extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(name = "title", nullable = false, length = 300)
    private String title = "";

    @Column(name = "coquan_id")
    private UUID coquanId;

    @Column(name = "coquan_identifier", length = 100)
    private String coquanIdentifier;

    @Column(name = "user_creator", length = 100)
    private String userCreator;

    @Column(name = "dynamic_fields", columnDefinition = "jsonb", nullable = false)
    private String dynamicFieldsJson = "{}";

    @Column(name = "version_no", nullable = false)
    private long versionNo = 1L;

    public VbpqDocument(String title, UUID coquanId, String coquanIdentifier, String userCreator) {
        this.id = UUID.randomUUID();
        this.title = title == null ? "" : title;
        this.coquanId = coquanId;
        this.coquanIdentifier = coquanIdentifier;
        this.userCreator = userCreator;
    }

    public VbpqDocument rename(String title) {
        if (title != null) {
            this.title = title;
        }
        this.versionNo += 1L;
        return this;
    }

    public VbpqDocument replaceDynamicFields(String dynamicFieldsJson) {
        this.dynamicFieldsJson = (dynamicFieldsJson == null || dynamicFieldsJson.isBlank()) ? "{}" : dynamicFieldsJson;
        return this;
    }
}
