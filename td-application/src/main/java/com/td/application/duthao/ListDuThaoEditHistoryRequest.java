package com.td.application.duthao;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Yêu cầu người dùng 27/08/2026 — request cho {@code POST /api/v1/duthao/VetHieuChinh}: đọc lịch sử
 * hiệu chỉnh theo dự thảo cha, tương đương {@code qlvb/danhmuc/GetRes?databasetype=duthao} phía
 * Domino (body cũ {parentid, items:"getall"}).
 */
@Data
public class ListDuThaoEditHistoryRequest {

    @NotNull(message = "parent_id không được để trống")
    private UUID parentId;

    private Integer pageNumber = 1;

    private Integer pageSize = 20;
}
