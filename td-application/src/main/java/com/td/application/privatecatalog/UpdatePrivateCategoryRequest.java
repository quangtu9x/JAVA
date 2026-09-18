package com.td.application.privatecatalog;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class UpdatePrivateCategoryRequest {

    @Size(min = 1, max = 100, message = "Mã danh mục tối đa 100 ký tự")
    private String code;

    @Size(min = 1, max = 300, message = "Tên danh mục tối đa 300 ký tự")
    private String name;

    private String description;

    @Size(max = 100, message = "Form tối đa 100 ký tự")
    private String form;

    private boolean updateParent = false;
    private UUID parentId;

    private Integer sortOrder;
    private Boolean isActive;

    // Mã định danh đơn vị/phòng ban (currentUser.identifier bên FE) — xem PrivateCategory.identifier.
    @Size(max = 200, message = "Identifier tối đa 200 ký tự")
    private String identifier;

    private Map<String, String> dynamicFields;

    private String dateFields;

    private String numberFields;

    private String richtextFields;

    @JsonSetter("parentId")
    public void setParentIdFromJson(UUID parentId) {
        this.parentId = parentId;
        this.updateParent = true;
    }
}
