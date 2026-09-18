package com.td.application.vbdi;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 11/09/2026 (yêu cầu người dùng "Lỗi cập nhật văn bản đến khi thao tác lưu văn bản đi... đọc và port
 * sang"): request cho {@code POST /api/v1/vbdi/CapNhatVanBanDi} — port action Domino
 * {@code capnhatvanbandi} (endpoint {@code qlvb/vbden/CapNhatVanBanDen?databasetype=vbden&
 * action=capnhatvanbandi&parentid=<vbdenId>}), gọi TỰ ĐỘNG mỗi khi lưu (Lưu bình thường, KHÔNG phải
 * "Vào sổ") 1 văn bản đi ĐÃ có liên kết {@code documentin_obj} (đã "Trả lời" ít nhất 1 văn bản đến
 * trước đó) — xem javadoc {@link CapNhatVanBanDiUseCase}.
 *
 * <p>KHÔNG nhận {@code documentIn} từ client (khác {@link TraLoiVanBanDenRequest}) — server tự đọc lại
 * {@code documentin_obj} đã lưu trên chính văn bản đi (đã persist từ hành động "Trả lời văn bản đến"
 * trước đó), tránh lặp lại lỗi ĐANG gặp ở FE Domino cũ (gửi nguyên {@code resdata.documentin_obj}
 * KHÔNG lọc — xem javadoc use case).</p>
 */
@Data
public class CapNhatVanBanDiRequest {

    @NotNull(message = "vbdiId khong duoc de trong")
    private UUID vbdiId;

    /** Mặc định "Cập nhật văn bản đi" nếu để trống (đúng literal FE cũ truyền cho action này). */
    private String opinion;

    private String creatorName;

    private String position;
}
