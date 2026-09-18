package com.td.application.hosoluutru;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Request dùng chung cho các hành động vòng đời đơn-field của hồ sơ lưu trữ:
 * {@link ChuyenLuuTruNoiBoUseCase}, {@link ChuyenLuuTruLichSuUseCase}, {@link ThuHoiLuuTruLichSuUseCase}.
 */
@Getter
@Setter
public class HoSoLuuTruLifecycleActionRequest {

    @NotNull
    private UUID hosoLuuTruId;

    /** Ý kiến (tuỳ chọn). */
    private String opinion;
}
