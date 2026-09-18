package com.td.application.hosocv;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G3: request cho {@code POST /api/v1/hosocv/DuyetHoSo}
 * ({@code duyet=true} → {@code trinhlanhdaoduyet="1"} đã duyệt; {@code duyet=false} →
 * {@code "3"} không được duyệt).
 */
@Data
public class DuyetHoSoRequest {

    @NotNull(message = "hosocvId khong duoc de trong")
    private UUID hosocvId;

    @NotNull(message = "duyet khong duoc de trong")
    private Boolean duyet;

    private String yKien;
}
