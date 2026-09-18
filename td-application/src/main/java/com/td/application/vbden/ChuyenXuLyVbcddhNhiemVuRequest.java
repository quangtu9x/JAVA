package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Request cho {@code POST /api/v1/vbden/ChuyenXuLyVbcddhNhiemVu} — tương thích API legacy
 * {@code qlvb/vbden/ChuyenXuLyVBCDDH} ({@code {listchutri, listphoihop, ykien}}, 2 field đầu Domino
 * gửi dạng JSON-string-hoá 1 mảng object — ở đây Java nhận thẳng mảng object đã parse sẵn, cùng
 * {@link NhiemVuCdDhAssigneeDto} đã dùng cho {@link GuiSangVbcddhRequest}).
 *
 * <p>KHÁC {@link GuiSangVbcddhRequest} (tạo MỚI nhiệm vụ khi vbden vừa chuyển thành vbcddh lần đầu):
 * request này chỉ ĐỒNG BỘ LẠI nhiệm vụ vbcddh ĐÃ TỒN TẠI (liên kết qua {@code idvbden}) mỗi khi vbden
 * gốc được "Chuyển xử lý" lần nữa — xem javadoc {@link ChuyenXuLyVbcddhNhiemVuUseCase}.</p>
 */
@Data
public class ChuyenXuLyVbcddhNhiemVuRequest {

    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    private List<NhiemVuCdDhAssigneeDto> listChuTri = new ArrayList<>();

    private List<NhiemVuCdDhAssigneeDto> listPhoiHop = new ArrayList<>();

    private String yKien;
}
