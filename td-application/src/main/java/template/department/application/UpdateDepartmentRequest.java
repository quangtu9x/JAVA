package template.department.application;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateDepartmentRequest {

    @Size(min = 1, max = 100, message = "Mã phòng ban tối đa 100 ký tự")
    private String code;

    @Size(min = 1, max = 300, message = "Tên phòng ban tối đa 300 ký tự")
    private String name;

    private String description;

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