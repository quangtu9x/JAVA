package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Yêu cầu người dùng 15/09/2026 ("kiểm tra lại nghiệp vụ văn bản đến ... port/cutover nốt phần còn
 * lại"): request cho {@code POST /api/v1/vbden/SuaYKien} — tương thích 1 PHẦN
 * {@code qlvb/vbden/CapNhatVanBanDen?databasetype=vbden_res&action=suanoidung} ({@code SuaYkien.jsx}
 * gọi) — xem javadoc {@link SuaYKienVbdenUseCase}. {@code id} là id của CHÍNH bản ghi
 * {@code vbden_res} (khác {@code parentid} dùng ở các request khác trong module này — bản ghi này SỬA
 * TRỰC TIẾP, không tạo bản ghi con nào).
 */
@Data
public class SuaYKienVbdenRequest {

    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    /** Nội dung ý kiến MỚI — form FE chỉ có đúng 1 field này (TextArea), rỗng hợp lệ (xoá ý kiến). */
    private String opinion;
}
