package com.td.application.vbden;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

/**
 * Kết quả {@code LuuVanBanDenUseCase} — {@code idso} rỗng nếu không "vào sổ" ở lần lưu này.
 *
 * <p>{@code userProcess} (bổ sung 22/08/2026, R6-lite — xem javadoc
 * {@code LuuVanBanDenUseCase#applyDefaultAssignerAsProcessor}): giá trị {@code user_process} SAU
 * CÙNG của văn bản ngay sau lần lưu này (nối {@code ";"} nếu nhiều người, rỗng nếu chưa ai) — kể cả
 * khi field này KHÔNG có trong request (backend tự mặc định = assigner). FE dùng field này (thay vì
 * chỉ đọc field gửi lên) để biết có cần gọi thêm {@code ChuyenXuLy} hay không.</p>
 */
@Data
@AllArgsConstructor
public class LuuVanBanDenResultDto {

    private UUID idvbden;

    private UUID idso;

    private String userProcess;
}
