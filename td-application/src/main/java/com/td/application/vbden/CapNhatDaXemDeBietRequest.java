package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Yêu cầu người dùng 15/09/2026 ("kiểm tra lại nghiệp vụ văn bản đến ... port/cutover nốt phần còn
 * lại"): request cho {@code POST /api/v1/vbden/CapNhatDaXemDeBiet} — tương thích
 * {@code qlvb/vbden/CapNhatVanBanDen?action=capnhatdaxemdebiet} ({@code CapNhatVanBanDen.java} thật,
 * hàm {@code daxemdebiet()}) — xem javadoc {@link CapNhatDaXemDeBietUseCase}. FE gọi với body RỖNG
 * (chỉ {@code parentid} qua query-string bản Domino cũ), không có opinion/creator/position.
 */
@Data
public class CapNhatDaXemDeBietRequest {

    @NotNull(message = "parentid khong duoc de trong")
    private UUID parentid;
}
