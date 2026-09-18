package com.td.application.vbdi;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026 — request cho {@code POST /api/v1/vbdi/SendLog}: đọc Nhật ký gửi
 * nhận theo văn bản đi cha, xem {@link ListVbdiSendLogUseCase}.
 */
@Data
public class ListVbdiSendLogRequest {

    @NotNull(message = "documentId khong duoc de trong")
    private UUID documentId;

    private Integer pageNumber = 1;

    private Integer pageSize = 20;
}
