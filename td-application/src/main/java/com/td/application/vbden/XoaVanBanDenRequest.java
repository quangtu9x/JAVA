package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Yêu cầu người dùng 14/09/2026 ("ở chi tiết văn bản đến, chức năng Xóa đang gọi API cũ ... kiểm
 * tra nếu chưa có thì port sang JAVA và cutoverFE") — tương thích {@code qlvb/vbden/XoaVanBanDen}
 * (Domino thật, {@code idvbden}/{@code idso} là 2 tham số query string) — xem javadoc
 * {@link XoaVanBanDenUseCase}.
 */
@Data
public class XoaVanBanDenRequest {

    @NotNull
    private UUID idvbden;

    // Domino cho phep rong (FE gui idso=${idso || ""}) - khi do CHI xoa noi dung dung chung
    // (idvbden), khong co ban ghi "so" rieng nao de xoa kem.
    private UUID idso;
}
