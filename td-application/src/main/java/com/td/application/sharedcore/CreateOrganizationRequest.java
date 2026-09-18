package com.td.application.sharedcore;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class CreateOrganizationRequest {

    @NotBlank(message = "Tên tổ chức không được để trống")
    @Size(min = 1, max = 300, message = "Tên tổ chức tối đa 300 ký tự")
    private String name;

    private Integer sort_order = 0;

    private Integer system = 0;

    private String receiver_id;

    private String receiver;

    private String receiver_position;

    private String parent;

    private String parentid;

    private Integer level;

    private String servername;

    private String server_id;

    private String ipserver;

    private String dbpath;

    // Bổ sung 19/08/2026: liên kết MỀM tới tenants.tenant_id (control-plane, không FK thật) — xem
    // javadoc Organization.linkedTenantId.
    private String linked_tenant_id;

    // Bổ sung 20/08/2026 (bugfix, BL-118 tiếp): trước đây tuỳ chọn — 1 Organization thiếu identifier
    // làm GÃY chuỗi identifiers (số nhiều) của MỌI user có tổ chức nằm dưới node đó trong cây (xem
    // GetUserOrganizationProfileUseCase.resolveOrganizationAncestry — nối "" vào chuỗi tại node này),
    // hậu quả tương tự CreateAppUserRequest ở trên. Bắt buộc ngay từ lúc tạo tổ chức.
    @NotBlank(message = "Mã định danh (identifier) không được để trống")
    @Size(max = 100, message = "Identifier tối đa 100 ký tự")
    @JsonAlias("code")
    private String identifier;

    @NotBlank(message = "Form không được để trống")
    private String form;

    private Boolean is_active = true;

    // Field tự định nghĩa từ FE, lưu vào cột dynamic_fields (JSONB) — cùng convention với
    // AddDynamicDocumentRequest (BL-101): dateFields/numberFields/richtextFields là chuỗi tên field
    // phân tách ";" để suy luận kiểu lưu trong JSON (mặc định TEXT nếu không khớp hậu tố/danh sách nào).
    private Map<String, String> dynamicFields = new LinkedHashMap<>();

    private String dateFields;

    private String numberFields;

    private String richtextFields;
}
