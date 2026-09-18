package com.td.application.vbcddh;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BL-250 (VBCDDH Core) — DoD (2): request cho {@code POST /api/v1/vbcddh/PhanCongNhiemVu} — tương
 * thích quy trình legacy {@code qlvb/add?databasetype=vbcddh} (form {@code nhiemvu}), gộp vòng lặp
 * "mỗi đơn vị 1 bản ghi" mà FE thật thực hiện (xem {@code NhiemVuChiTietPage.jsx}) thành 1 lần gọi.
 */
@Data
public class PhanCongNhiemVuRequest {

    @NotNull(message = "idVanBan khong duoc de trong")
    private UUID idVanBan;

    @NotEmpty(message = "assignments khong duoc de trong")
    @Valid
    private List<NhiemVuAssignment> assignments = new ArrayList<>();
}
