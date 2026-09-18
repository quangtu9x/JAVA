package com.td.application.hosocv;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G3: request cho {@code POST /api/v1/hosocv/ChuyenLuuTru}.
 *
 * <p><b>Mở rộng 08/09/2026</b> (port module {@code hosoluutru} —
 * {@code docs/analysis/16-phan-tich-port-hosoluutru-domino-sang-java.md} mục 5.6/8.4): thêm
 * {@code dmhsId} — id danh mục hồ sơ lưu trữ ({@code records_storage_catalogs}, BL-116) người dùng
 * chọn ở modal "Chuyển sang hồ sơ lưu trữ" (đúng bằng chứng FE
 * {@code hosocv/Components/ActionModal.jsx} tham số {@code dmhsid}) — BẮT BUỘC, vì
 * {@link ChuyenLuuTruHoSoUseCase} giờ tạo LUÔN bản ghi {@code hosoluutru} tương ứng (không còn dừng ở
 * {@code storaged="2"} như bản giới hạn cũ).</p>
 */
@Data
public class ChuyenLuuTruHoSoRequest {

    @NotNull(message = "hosocvId khong duoc de trong")
    private UUID hosocvId;

    @NotNull(message = "dmhsId khong duoc de trong")
    private UUID dmhsId;
}
