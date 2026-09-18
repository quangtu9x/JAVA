package com.td.application.hosocv;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G3: request cho {@code POST /api/v1/hosocv/TrinhDuyet}
 * (trình lãnh đạo duyệt hồ sơ, {@code trinhlanhdaoduyet: "" → "2"}).
 */
@Data
public class TrinhDuyetHoSoRequest {

    @NotNull(message = "hosocvId khong duoc de trong")
    private UUID hosocvId;

    /** Người/đơn vị duyệt được chọn khi trình — tuỳ chọn, ghi vào {@code user_approved_by} nếu có. */
    private String nguoiDuyet;

    private String ghiChu;
}
