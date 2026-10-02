package com.td.application.sharedcore;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class CreateAppUserRequest {

    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Size(max = 100, message = "Tên đăng nhập tối đa 100 ký tự")
    private String username;

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(max = 300, message = "Họ và tên tối đa 300 ký tự")
    private String fullName;

    private String email;

    // Bổ sung 20/08/2026 (bugfix, BL-118 tiếp): trước đây tuỳ chọn — hậu quả: user tạo không gán tổ
    // chức/cấp khiến qlvb/profile trả identifiers/level rỗng, làm gãy MỌI tính năng phụ thuộc
    // getIdentifier_by_level(currentUser) phía FE (chọn "Sổ văn bản" khi Nhập mới văn bản đến, và
    // các hàm cùng họ trong utils/global.jsx — xem docs/migration-backlog/04-migration-status.md
    // mục "BL-118 tiếp, 6"). Bắt buộc NGAY từ lúc tạo — không bắt buộc ở UpdateAppUserRequest vì đó
    // là partial update (null = giữ nguyên, không phải "xoá"), không hợp lý ép luôn phải gửi kèm.
    @NotNull(message = "Cơ cấu tổ chức không được để trống")
    private UUID organizationId;

    private UUID positionId;

    private Boolean isActive = true;

    @NotNull(message = "Cấp (trong cây cơ cấu tổ chức) không được để trống")
    private Integer level;

    // 24/09/2026 (yêu cầu người dùng): mặc định 99 thay cho 0 — danh sách sắp xếp TĂNG DẦN theo thứ tự
    // hiển thị, để 0 thì người dùng mới luôn nhảy lên đầu. Người nhập giá trị cụ thể vẫn giữ nguyên.
    public static final int DEFAULT_SORT_ORDER = 99;

    private Integer sortOrder = DEFAULT_SORT_ORDER;

    private String phoneNumber;

    private String gender;

    private LocalDate birthdayDate;

    private String address;

    private String userIdNo;

    private String capLanhDao;

    @Size(max = 100, message = "Mã định danh tối đa 100 ký tự")
    private String identifier;

    private Boolean isSim = false;

    // Chưa dùng ở BL-108 (UseCase quản lý kiêm nhiệm để đợt sau) — nhận sẵn field để không phải
    // đổi request DTO khi triển khai tiếp.
    private UUID parentUserId;

    // "Nhóm vai trò" (BL-111) — 1 user có thể thuộc NHIỀU nhóm (N:N, xem AppUserRoleGroup.java, bổ
    // sung 19/08/2026 — sửa lại giả định ban đầu "chỉ 1 nhóm/user" cho đúng thực tế).
    private List<UUID> roleGroupIds = new ArrayList<>();

    // Field tự định nghĩa từ FE, lưu vào cột dynamic_fields (JSONB) — cùng convention với
    // Create/UpdateOrganizationRequest (BL-109): dateFields/numberFields/richtextFields là chuỗi
    // tên field phân tách ";" để suy luận kiểu lưu trong JSON.
    private Map<String, String> dynamicFields = new LinkedHashMap<>();

    private String dateFields;

    private String numberFields;

    private String richtextFields;
}
