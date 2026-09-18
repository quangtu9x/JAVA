package com.td.application.vbcddh;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * BL-250 (VBCDDH Core) — DoD (3): request cho {@code POST /api/v1/vbcddh/CapNhatTienDo}. Domino
 * thật ({@code ActionModal.jsx}) có 2 hành động khác nhau trên cùng nhiệm vụ: {@code capnhattiendo}
 * (cập nhật tiến độ — chỉ ghi ý kiến, chưa kết thúc) và {@code capnhatketquaxuly} (kết thúc, set
 * {@code tinhtrang="Đã thực hiện"} trên nhiemvu cha).
 */
@Data
public class CapNhatTienDoRequest {

    @NotNull(message = "idNhiemVu khong duoc de trong")
    private UUID idNhiemVu;

    /** {@code capnhattiendo} (mặc định) hoặc {@code capnhatketquaxuly}. */
    private String action = "capnhattiendo";

    private String yKien;
}
