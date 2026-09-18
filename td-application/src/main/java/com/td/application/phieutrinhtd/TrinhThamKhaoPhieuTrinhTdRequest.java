package com.td.application.phieutrinhtd;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Request cho {@code POST /api/v1/phieutrinhtd/TrinhThamKhao} —
 * {@link TrinhThamKhaoPhieuTrinhTdUseCase} (tương đương
 * {@code PhieuTrinhActionModal.jsx#actionname==="trinhthamkhao"}, xem tài liệu 20 mục 2.3).
 */
@Data
public class TrinhThamKhaoPhieuTrinhTdRequest {

    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    /** Tên hiển thị người được hỏi ý kiến, nối {@code ";"} — field {@code nguoithamkhao}. */
    private String nguoiThamKhao;

    /** Username người được hỏi ý kiến, nối {@code ";"} — field {@code nguoithamkhaohide}. */
    private String nguoiThamKhaoHide;

    private String opinion;
}
