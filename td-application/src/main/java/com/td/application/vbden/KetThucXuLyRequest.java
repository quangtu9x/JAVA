package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * N17 (04/09/2026 — "Kết thúc xử lý", nút dùng chung bởi hầu hết màn xử lý vbden qua
 * {@code ChoYKienModal.jsx} với {@code action="ketthucxuly"} — xem javadoc {@link KetThucXuLyUseCase}):
 * request cho {@code POST /api/v1/vbden/KetThucXuLy} — tương thích 1 PHẦN {@code qlvb/vbden/
 * CapNhatVanBanDen?action=ketthucxuly} ({@code CapNhatVanBanDen.java} thật, hàm {@code ketthucxuly()}).
 */
@Data
public class KetThucXuLyRequest {

    @NotNull(message = "parentid khong duoc de trong")
    private UUID parentid;

    /** Ý kiến kết thúc xử lý — FE ({@code ChoYKienModal.jsx}) gửi field {@code opinion}, không bắt buộc. */
    private String opinion;

    /** Tên hiển thị người thực hiện — chỉ để hiển thị (giống {@link CapNhatKetQuaXuLyRequest#getCreatorName()}). */
    private String creatorName;

    /** Chức vụ hiển thị — chỉ để hiển thị. */
    private String position;

    /**
     * Bổ sung 14/09/2026 (yêu cầu người dùng "làm luôn chức năng Gắn văn bản trả lời ở modal này
     * luôn") — tương đương {@code res_obj} phía Domino, xem javadoc {@link VbdenReplyDocumentRef} và
     * {@link KetThucXuLyUseCase}. Rỗng/không gửi = không gắn văn bản trả lời (hành vi cũ, không đổi).
     */
    private List<VbdenReplyDocumentRef> resObj;
}
