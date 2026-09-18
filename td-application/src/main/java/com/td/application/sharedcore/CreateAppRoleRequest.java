package com.td.application.sharedcore;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

// BL-111: Vai trò người dùng — CRUD "Vai trò" (AppRole), trước chỉ có Get/Search (đọc).
@Data
public class CreateAppRoleRequest {

    // Không bắt buộc: form "Vai trò" phía Domino gốc chỉ có "Tên vai trò", không có ô nhập mã
    // riêng — CreateAppRoleUseCase tự sinh code từ name khi không được gửi (giống agency_level tự
    // sinh identifier từ name trong CreateOrganizationUseCase, BL-105).
    @Size(max = 100, message = "Mã vai trò tối đa 100 ký tự")
    private String code;

    @NotBlank(message = "Tên vai trò không được để trống")
    @Size(max = 200, message = "Tên vai trò tối đa 200 ký tự")
    private String name;

    private String description;

    private Boolean isSystemRole = false;

    private Boolean isActive = true;

    private Integer sortOrder = 0;

    // Field tự định nghĩa từ FE (menu_top, menu_vbden, menu_duthao, menu_hosocv, menu_phieutrinh,
    // menu_vbdi, menu_vbcddh, menu_lichct, menu_chiasetep, menu_qlvb, menu_tlch, menu_hosoluutru,
    // access_level — mỗi field 1 chuỗi ";" theo đúng quy ước Domino cũ), lưu vào dynamic_fields
    // (JSONB) — cùng convention với Create/UpdateOrganizationRequest (BL-109).
    private Map<String, String> dynamicFields = new LinkedHashMap<>();

    private String dateFields;

    private String numberFields;

    private String richtextFields;
}
