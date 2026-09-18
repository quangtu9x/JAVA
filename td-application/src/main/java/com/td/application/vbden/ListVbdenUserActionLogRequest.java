package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 18/09/2026 — request cho {@code POST /api/v1/vbden/GetLogUserAction}: đọc "Lịch sử thao tác văn
 * bản" theo văn bản đến cha — mirror {@code com.td.application.vbdi.ListVbdiUserActionLogRequest}.
 */
@Data
public class ListVbdenUserActionLogRequest {

    @NotNull(message = "documentId khong duoc de trong")
    private UUID documentId;

    private Integer pageNumber = 1;

    private Integer pageSize = 200;
}
