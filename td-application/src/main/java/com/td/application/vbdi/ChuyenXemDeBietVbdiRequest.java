package com.td.application.vbdi;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Request cho {@code POST /api/v1/vbdi/ChuyenXemDeBiet} — route FE {@code vbdi/xuly/nhandebiet}, nút
 * "Chuyển xem để biết" (xem javadoc {@link ChuyenXemDeBietVbdiUseCase}). Ít nhất 1 trong 2 danh sách
 * {@code coordinators}/{@code internalUnits} phải có phần tử (đúng Domino: mỗi nhóm chỉ xử lý khi
 * form field tương ứng không rỗng).
 */
@Data
public class ChuyenXemDeBietVbdiRequest {

    @NotNull(message = "documentId khong duoc de trong")
    private UUID documentId;

    private String opinion;

    /** Cá nhân được chọn qua {@code ModalNguoiSuDung} — khớp theo {@code username}. */
    private List<VbdiForwardReadRecipient> coordinators;

    /** Đơn vị nội bộ được chọn qua {@code ModalDonViPhongBanNoiBo} — khớp theo {@code identifier}. */
    private List<VbdiForwardReadRecipient> internalUnits;
}
