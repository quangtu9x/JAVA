package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Yêu cầu người dùng 24/09/2026 — request cho {@code POST /api/v1/vbden/LichSuCapNhat}: lịch sử "Cập
 * nhật VB" của 1 văn bản đến (id văn bản GỐC, {@code resdata.id} ở FE).
 */
@Data
public class ListVbdenUpdateHistoryRequest {

    @NotNull(message = "parent_id không được để trống")
    private UUID parentId;

    private Integer pageNumber = 1;

    private Integer pageSize = 20;
}
