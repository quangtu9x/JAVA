package com.td.application.categories;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCategoryRequest {

    @NotBlank(message = "Mã danh mục không được để trống")
    @Size(min = 1, max = 100, message = "Mã danh mục tối đa 100 ký tự")
    private String code;

    @NotBlank(message = "Tên danh mục không được để trống")
    @Size(min = 1, max = 300, message = "Tên danh mục tối đa 300 ký tự")
    private String name;

    private String description;

    @Size(max = 100, message = "Form tối đa 100 ký tự")
    private String form;

    /** null = tạo ở cấp gốc */
    private UUID parentId;

    private int sortOrder = 0;
}
