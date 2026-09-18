package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * N15 (03/09/2026 — "Cập nhật kết quả xử lý", nút {@code VBDenButtonActionVT.jsx#btn_vanthu_ketthuc_xuly}):
 * request cho {@code POST /api/v1/vbden/CapNhatKetQuaXuLy} — tương thích 1 PHẦN {@code qlvb/vbden/
 * CapNhatVanBanDen?action=vanthuketthucxuly} ({@code CapNhatVanBanDen.java} thật, hàm
 * {@code vanthuketthucxuly()} — xem lý do CHỈ port 1 phần trong javadoc {@link CapNhatKetQuaXuLyUseCase}).
 */
@Data
public class CapNhatKetQuaXuLyRequest {

    @NotNull(message = "parentid khong duoc de trong")
    private UUID parentid;

    /** Ý kiến kết thúc xử lý — FE ({@code ChoYKienModal.jsx}) gửi field {@code opinion}, không bắt buộc. */
    private String opinion;

    /** Tên hiển thị người thực hiện — chỉ để hiển thị (giống {@link ChuyenXuLyVbdenRequest#getCreatorName()}). */
    private String creatorName;

    /** Chức vụ hiển thị — chỉ để hiển thị. */
    private String position;

    /**
     * Bổ sung 14/09/2026 (yêu cầu người dùng "làm luôn chức năng Gắn văn bản trả lời ở modal này
     * luôn") — tương đương {@code res_obj} phía Domino, xem javadoc {@link VbdenReplyDocumentRef} và
     * {@link CapNhatKetQuaXuLyUseCase}. Rỗng/không gửi = không gắn văn bản trả lời (hành vi cũ, không
     * đổi).
     */
    private List<VbdenReplyDocumentRef> resObj;
}
