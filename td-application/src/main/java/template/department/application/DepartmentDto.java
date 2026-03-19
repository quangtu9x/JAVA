package template.department.application;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class DepartmentDto {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private UUID parentId;
    private int level;
    private String fullPath;
    private int sortOrder;
    private boolean isActive;
    private LocalDateTime createdOn;
    private LocalDateTime lastModifiedOn;
}