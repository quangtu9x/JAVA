package com.td.application.sharedcore;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateOrganizationRequest {

    @Size(min = 1, max = 100, message = "Mã tổ chức tối đa 100 ký tự")
    private String code;

    @Size(min = 1, max = 300, message = "Tên tổ chức tối đa 300 ký tự")
    private String name;

    private String description;

    private boolean updateParent = false;
    private UUID parentId;

    @NotBlank(message = "Form không được để trống")
    private String form;

    private Integer sortOrder;
    private Boolean isActive;

    @JsonSetter("parentId")
    public void setParentIdFromJson(UUID parentId) {
        this.parentId = parentId;
        this.updateParent = true;
    }

    @JsonSetter("parent_id")
    public void setParentIdFromLegacyJson(UUID parentId) {
        this.parentId = parentId;
        this.updateParent = true;
    }

    @JsonSetter("parent")
    public void setParentIdFromShortLegacyJson(UUID parentId) {
        this.parentId = parentId;
        this.updateParent = true;
    }
}
