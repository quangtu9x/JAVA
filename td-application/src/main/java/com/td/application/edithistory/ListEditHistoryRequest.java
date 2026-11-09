package com.td.application.edithistory;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 30/10/2026 — request cho {@code POST /api/v1/vethieuchinh/Xem}, thay
 * {@code qlvb/danhmuc/GetRes?databasetype=...} ({parentid, items:"getall"}) mà VetHieuChinh.jsx gọi.
 */
@Data
public class ListEditHistoryRequest {

    @NotBlank(message = "parent_id không được để trống")
    private String parentId;

    private Integer pageNumber = 1;

    private Integer pageSize = 20;
}
