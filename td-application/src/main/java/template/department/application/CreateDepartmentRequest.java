package template.department.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateDepartmentRequest {

    @NotBlank(message = "Mã phòng ban không được để trống")
    @Size(min = 1, max = 100, message = "Mã phòng ban tối đa 100 ký tự")
    private String code;

    @NotBlank(message = "Tên phòng ban không được để trống")
    @Size(min = 1, max = 300, message = "Tên phòng ban tối đa 300 ký tự")
    private String name;

    private String description;

    private UUID parentId;

    private int sortOrder = 0;
}