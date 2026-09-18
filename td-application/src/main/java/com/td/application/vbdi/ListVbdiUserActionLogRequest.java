package com.td.application.vbdi;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 30/08/2026 — request cho {@code POST /api/v1/vbdi/GetLogUserAction}: đọc "Lịch sử thao tác văn
 * bản" theo văn bản đi cha — port Domino {@code action=getloguseraction}
 * ({@code LogUserAction.java#getLogUserAction}). Domino gốc trả HẾT (không phân trang) — ở đây thêm
 * {@code pageSize} với mặc định/giới hạn RỘNG (200, xem {@link ListVbdiUserActionLogUseCase}) để
 * không đổi hành vi thực tế (số thao tác/văn bản hiếm khi vượt ngưỡng này) nhưng vẫn có chặn trên an
 * toàn, đúng tiền lệ {@code ListVbdiSendLogRequest}.
 */
@Data
public class ListVbdiUserActionLogRequest {

    @NotNull(message = "documentId khong duoc de trong")
    private UUID documentId;

    private Integer pageNumber = 1;

    private Integer pageSize = 200;
}
