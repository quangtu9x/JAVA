package com.td.application.categories;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Data
public class CreateCategoryRequest {

    // Không bắt buộc (BL-113): không phải form FE nào tạo Category cũng có ô nhập "code" riêng (vd
    // "Nhóm cơ quan ngoài" chỉ có "Tên") — CreateCategoryUseCase tự sinh code từ name khi không được
    // gửi, cùng cách CreateAppRoleUseCase tự sinh code cho "Vai trò" (BL-111).
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

    // Field tự định nghĩa từ FE, lưu vào cột dynamic_fields (JSONB) — cùng convention với
    // Create/UpdateOrganizationRequest (BL-109): dateFields/numberFields/richtextFields là chuỗi
    // tên field phân tách ";" để suy luận kiểu lưu trong JSON.
    private Map<String, String> dynamicFields = new LinkedHashMap<>();

    private String dateFields;

    private String numberFields;

    private String richtextFields;
}
