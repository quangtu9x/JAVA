package com.td.application.categories;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateCategoryRequest {

    @Size(min = 1, max = 100, message = "Mã danh mục tối đa 100 ký tự")
    private String code;

    @Size(min = 1, max = 300, message = "Tên danh mục tối đa 300 ký tự")
    private String name;

    private String description;

    /**
     * Cờ nội bộ - true khi FE đã truyền parentId vào body.
     * Nếu false: giữ nguyên parent hiện tại.
     * Nếu true + parentId null: chuyển lên gốc.
     */
    private boolean updateParent = false;
    private UUID parentId;

    private Integer sortOrder;
    private Boolean isActive;

    @JsonSetter("parentId")
    public void setParentIdFromJson(UUID parentId) {
        this.parentId = parentId;
        this.updateParent = true;
    }
}
