package com.td.application.vbdi;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026 — request cho {@code POST /api/v1/vbdi/VetHieuChinh}: đọc lịch sử
 * hiệu chỉnh theo văn bản đi cha, tương đương {@code qlvb/danhmuc/GetRes?databasetype=vbdi} phía
 * Domino (body cũ {parentid, items:"getall"}) — mirror {@code ListDuThaoEditHistoryRequest}.
 */
@Data
public class ListVbdiEditHistoryRequest {

    @NotNull(message = "parent_id không được để trống")
    private UUID parentId;

    private Integer pageNumber = 1;

    private Integer pageSize = 20;
}
