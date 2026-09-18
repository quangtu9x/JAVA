package com.td.application.privatecatalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Data
public class CreatePrivateCategoryRequest {

    // Không bắt buộc (cùng quyết định Category, BL-113): không phải form FE nào cũng có ô nhập
    // "code" riêng — CreatePrivateCategoryUseCase tự sinh từ name khi không gửi.
    @Size(max = 100, message = "Mã danh mục tối đa 100 ký tự")
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

    // Mã định danh đơn vị/phòng ban (currentUser.identifier bên FE) — xem PrivateCategory.identifier.
    @Size(max = 200, message = "Identifier tối đa 200 ký tự")
    private String identifier;

    private Map<String, String> dynamicFields = new LinkedHashMap<>();

    private String dateFields;

    private String numberFields;

    private String richtextFields;
}
