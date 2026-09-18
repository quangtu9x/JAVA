package com.td.application.vbden;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 16/09/2026 (bugfix người dùng: "route vbden/xuly/theodoi -> Bỏ theo dõi -> báo thành công nhưng
 * không mất khỏi danh sách, khéo nhầm chức năng"): request cho
 * {@code POST /api/v1/vbden/TheoDoiVanBan} — port {@code CapNhatVanBanDen.java#huytheodoi()}/
 * {@code #theodoilai()} thật (KHÔNG PHẢI cùng tính năng với {@link TheoDoiNhiemVuUseCase} — xem
 * javadoc {@link TheoDoiVanBanDenUseCase} để biết đầy đủ sự khác biệt).
 */
@Data
public class TheoDoiVanBanDenRequest {

    @NotNull(message = "parentid khong duoc de trong")
    private UUID parentid;

    /** {@code huytheodoi} (bỏ theo dõi) | {@code theodoilai} (theo dõi lại). */
    @NotBlank(message = "action khong duoc de trong")
    private String action;
}
