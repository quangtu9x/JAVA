package com.td.domain.documents;

import com.td.domain.common.contracts.BaseEntity;
import com.td.domain.common.contracts.IAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * BL-103 (Audit Log): bản ghi append-only cho mọi thao tác CRUD trên {@link BusinessDocument}.
 * Không extends {@code AuditableEntity} (không có soft-delete/update) — log là bất biến, không có
 * phương thức {@code update()}, không có setter công khai sau khi tạo.
 */
@Entity
@Table(name = "document_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentLog extends BaseEntity implements IAggregateRoot {

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "log_type", nullable = false, length = 30)
    private String logType;

    @Column(name = "log_data", columnDefinition = "jsonb", nullable = false)
    private String logDataJson;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public DocumentLog(UUID documentId, String logType, String logDataJson, String createdBy) {
        this.documentId = documentId;
        this.logType = logType;
        this.logDataJson = (logDataJson == null || logDataJson.isBlank()) ? "{}" : logDataJson;
        this.createdBy = (createdBy == null || createdBy.isBlank()) ? "system" : createdBy;
        this.createdAt = LocalDateTime.now();
    }
}
