package com.td.application.sharedcore;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateOrganizationRequest {

    @NotBlank(message = "Mã tổ chức không được để trống")
    @Size(min = 1, max = 100, message = "Mã tổ chức tối đa 100 ký tự")
    private String code;

    @NotBlank(message = "Tên tổ chức không được để trống")
    @Size(min = 1, max = 300, message = "Tên tổ chức tối đa 300 ký tự")
    private String name;

    private String description;

    @JsonAlias({"parent_id", "parent"})
    private UUID parentId;

    @NotBlank(message = "Form không được để trống")
    private String form;

    private int sortOrder = 0;

    private Boolean isActive = true;
}
