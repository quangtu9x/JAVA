package com.td.domain.numbering;

import com.td.domain.common.contracts.BaseEntity;
import com.td.domain.common.contracts.IAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * BL-104 (Document Numbering Service): cấu hình format sinh số văn bản theo từng
 * {@code documentType} (vd. {@code vbden}, {@code vbdi}).
 */
@Entity
@Table(name = "numbering_configs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NumberingConfig extends BaseEntity implements IAggregateRoot {

    public static final String RESET_POLICY_YEARLY = "YEARLY";

    @Column(name = "document_type", nullable = false, length = 50, unique = true)
    private String documentType;

    @Column(name = "format", nullable = false, length = 100)
    private String format;

    @Column(name = "reset_policy", nullable = false, length = 20)
    private String resetPolicy;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public NumberingConfig(String documentType, String format, String resetPolicy) {
        this.documentType = documentType;
        this.format = format;
        this.resetPolicy = (resetPolicy == null || resetPolicy.isBlank()) ? RESET_POLICY_YEARLY : resetPolicy;
        this.active = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public NumberingConfig update(String format, Boolean active) {
        if (format != null && !format.isBlank()) {
            this.format = format;
        }
        if (active != null) {
            this.active = active;
        }
        this.updatedAt = LocalDateTime.now();
        return this;
    }
}
