package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Yêu cầu người dùng 12/09/2026 — request cho {@code POST /api/v1/vbden/VetHieuChinh}: đọc lịch sử
 * hiệu chỉnh theo văn bản đến cha, tương đương {@code qlvb/danhmuc/GetRes?databasetype=vbden} phía
 * Domino (body cũ {parentid, items:"getall"}) — mirror {@code ListVbdiEditHistoryRequest}.
 */
@Data
public class ListVbdenEditHistoryRequest {

    @NotNull(message = "parent_id không được để trống")
    private UUID parentId;

    private Integer pageNumber = 1;

    private Integer pageSize = 20;
}
