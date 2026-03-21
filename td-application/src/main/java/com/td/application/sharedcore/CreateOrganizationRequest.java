package com.td.application.sharedcore;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

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

    @Size(max = 100, message = "Identifier tối đa 100 ký tự")
    @JsonAlias("code")
    private String identifier;

    @NotBlank(message = "Form không được để trống")
    private String form;

    private Boolean is_active = true;
}
