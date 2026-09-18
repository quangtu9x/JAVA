package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Bổ sung 07/09/2026 (4, yêu cầu người dùng: "port luôn chức năng 'Không vào sổ'") — request cho
 * {@code POST /api/v1/vbden/KhongVaoSo}, tương thích {@code CapNhatVanBanDen.java} thật, hàm
 * {@code khongvaoso()} — xem javadoc {@link KhongVaoSoVbdenUseCase}.
 *
 * <p><b>KHÁC {@link TuChoiTiepNhanVbdenRequest}</b>: đây là chức năng ĐỘC LẬP, không kèm phản hồi
 * trạng thái cho đơn vị gửi (Domino thật KHÔNG gọi {@code ResponseStatus} trong {@code khongvaoso()})
 * — dùng bởi các màn hình KHÁC (vào sổ hàng loạt/đơn lẻ chọn "Không vào sổ" thay vì "Từ chối"/
 * "Trả lại") — 2 chức năng bị nhầm lẫn/gộp chung ở lần port trước, nay tách rõ theo mô tả người
 * dùng.</p>
 */
@Data
public class KhongVaoSoVbdenRequest {

    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    /**
     * Lý do không vào sổ — FE luôn thu thập (bắt buộc nhập) nhưng {@code khongvaoso()} thật KHÔNG
     * đọc/lưu field này ở bất kỳ đâu (đối chiếu trực tiếp mã nguồn, không suy đoán) — giữ lại trong
     * request để KHÔNG phá vỡ hợp đồng API với FE hiện có, nhưng KHÔNG ghi vào đâu, đúng Domino thật.
     */
    private String opinion;
}
