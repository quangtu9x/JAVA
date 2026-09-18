package com.td.application.hosoluutru;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Request dùng chung cho các hành động đơn-field trên phiếu yêu cầu:
 * {@link DuyetPhieuYeuCauUseCase}, {@link ThuHoiYeuCauMuonUseCase}, {@link TraTaiLieuUseCase},
 * {@link ThuHoiHoSoDaChoMuonUseCase}.
 */
@Getter
@Setter
public class PhieuYeuCauActionRequest {

    @NotNull
    private UUID phieuId;
}
