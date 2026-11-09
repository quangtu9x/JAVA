package com.td.domain.chiasetep;

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
 * 30/10/2026 (port module "Quản lý tài liệu" - Domino chiasetep.nsf): 1 bản ghi là thư mục
 * ({@link #FORM_THU_MUC}) hoặc tài liệu ({@link #FORM_TAI_LIEU}); cây theo {@code parentId}
 * ({@link #ROOT_PARENT} = thư mục gốc). Field biến thiên nằm trong {@code dynamic_fields}.
 */
@Entity
@Table(name = "chiasetep_documents")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChiaSeTepDocument extends AuditableEntity<UUID> implements IAggregateRoot {

    public static final String FORM_THU_MUC = "thumuc";
    public static final String FORM_TAI_LIEU = "tailieu";
    public static final String ROOT_PARENT = "#";

    @Column(name = "form", nullable = false, length = 50)
    private String form;

    @Column(name = "parent_id", length = 100)
    private String parentId;

    @Column(name = "title", nullable = false, length = 300)
    private String title = "";

    @Column(name = "user_creator", length = 100)
    private String userCreator;

    @Column(name = "dynamic_fields", columnDefinition = "jsonb", nullable = false)
    private String dynamicFieldsJson = "{}";

    @Column(name = "version_no", nullable = false)
    private long versionNo = 1L;

    public ChiaSeTepDocument(String form, String parentId, String title, String userCreator) {
        this.id = UUID.randomUUID();
        this.form = form;
        this.parentId = parentId;
        this.title = title == null ? "" : title;
        this.userCreator = userCreator;
    }

    public ChiaSeTepDocument rename(String title) {
        if (title != null) {
            this.title = title;
        }
        this.versionNo += 1L;
        return this;
    }

    public ChiaSeTepDocument moveTo(String parentId) {
        this.parentId = parentId;
        return this;
    }

    public ChiaSeTepDocument replaceDynamicFields(String dynamicFieldsJson) {
        this.dynamicFieldsJson = (dynamicFieldsJson == null || dynamicFieldsJson.isBlank()) ? "{}" : dynamicFieldsJson;
        return this;
    }

    public boolean isFolder() {
        return FORM_THU_MUC.equalsIgnoreCase(form);
    }
}
