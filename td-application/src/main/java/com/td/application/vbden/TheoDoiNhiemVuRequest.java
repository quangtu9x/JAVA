package com.td.application.vbden;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * N20 (07/09/2026 — "Văn bản cần theo dõi", {@code toanbovanban/components/ItemsListNhiemVuCanTheoDoi.jsx}
 * hàm {@code action_theodoi} — xem javadoc {@link TheoDoiNhiemVuUseCase}): request cho
 * {@code POST /api/v1/vbden/TheoDoiNhiemVu} — tương thích 1 PHẦN {@code qlvb/vbden/TheoDoiNhiemVu}
 * ({@code TheoDoiNhiemVu.java} thật).
 */
@Data
public class TheoDoiNhiemVuRequest {

    @NotNull(message = "parentid khong duoc de trong")
    private UUID parentid;

    /** {@code theodoi} (đánh dấu theo dõi) | {@code huytheodoi} (bỏ theo dõi) | {@code chuyentheodoi}
     * (Domino gốc — hàm {@code chuyentheodoi()} — RỖNG, không làm gì, xem javadoc use case). */
    @NotBlank(message = "action khong duoc de trong")
    private String action;
}
