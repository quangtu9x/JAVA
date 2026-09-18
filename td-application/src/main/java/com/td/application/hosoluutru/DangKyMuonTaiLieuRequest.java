package com.td.application.hosoluutru;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/** Request cho {@link DangKyMuonTaiLieuUseCase} — đúng {@code PhieuYeuCauModal.jsx} (tạo phiếu). */
@Data
public class DangKyMuonTaiLieuRequest {

    @NotNull(message = "hosoId khong duoc de trong")
    private UUID hosoId;

    @NotBlank(message = "returnDate (ngay hen tra) khong duoc de trong")
    private String returnDate;

    @NotBlank(message = "subject (muc dich muon) khong duoc de trong")
    private String subject;
}
