package com.td.application.sharedcore;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateOrganizationRequest {

    @Size(min = 1, max = 300, message = "Tên tổ chức tối đa 300 ký tự")
    private String name;

    private Integer sort_order;

    private Integer system;

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

    // Bổ sung 19/08/2026: liên kết MỀM tới tenants.tenant_id (control-plane, không FK thật) — null =
    // không đổi, chuỗi rỗng = gỡ liên kết, xem javadoc Organization.linkedTenantId/updateLinkedTenant.
    private String linked_tenant_id;

    @Size(max = 100, message = "Identifier tối đa 100 ký tự")
    @JsonAlias("code")
    private String identifier;

    @NotBlank(message = "Form không được để trống")
    private String form;

    private Boolean is_active;

    // Field tự định nghĩa từ FE — null nghĩa là không đổi field động (partial update, cùng
    // convention với các field khác trong request này); có giá trị thì MERGE vào field động hiện
    // có (không xoá field cũ không được nhắc tới), xem UpdateOrganizationUseCase.
    private Map<String, String> dynamicFields;

    private String dateFields;

    private String numberFields;

    private String richtextFields;
}
