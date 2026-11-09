package com.td.domain.edithistory;

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
 * 30/10/2026 (cutover qlvb/add): bảng {@code edit_history_documents} — vết hiệu chỉnh DÙNG CHUNG cho
 * các màn còn gọi {@code LogVariable.getChangedFieldDetails} mặc định (danh mục, người dùng, đơn vị,
 * sổ văn bản, ...). Cùng khuôn {@code VbdenEditHistoryDocument} nhưng {@code parentId} là chuỗi vì bản
 * ghi cha nằm ở nhiều bảng/DB khác nhau. APPEND-ONLY.
 */
@Entity
@Table(name = "edit_history_documents")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EditHistoryDocument extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(name = "parent_id", nullable = false, length = 100)
    private String parentId;

    /** databasetype FE gửi (qlvb/tudien/hosocv/...) — chỉ để tra cứu. */
    @Column(name = "database_type", length = 50)
    private String databaseType;

    @Column(name = "form_edit", length = 100)
    private String formEdit;

    /** JSON {@code {field:{oldvalue,newvalue}}}. */
    @Column(name = "edit_obj", columnDefinition = "jsonb", nullable = false)
    private String editObjJson;

    @Column(name = "creator_name", length = 300)
    private String creatorName;

    /** Username người hiệu chỉnh — server gán qua CurrentUserProvider. */
    @Column(name = "user_creator", length = 100)
    private String userCreator;

    public EditHistoryDocument(String parentId, String databaseType, String formEdit, String editObjJson,
                               String creatorName, String userCreator) {
        this.id = UUID.randomUUID();
        this.parentId = parentId;
        this.databaseType = databaseType;
        this.formEdit = formEdit;
        this.editObjJson = (editObjJson == null || editObjJson.isBlank()) ? "{}" : editObjJson;
        this.creatorName = creatorName;
        this.userCreator = userCreator;
    }
}
