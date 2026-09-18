package com.td.application.duthao;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026 ("port lại XuLyDuThao.java, class này có các chức năng tương ứng ở
 * button của văn bản đi"): request DÙNG CHUNG cho {@code POST /api/v1/duthao/TraLaiChoLanhDao} và
 * {@code POST /api/v1/duthao/TraLaiChoNguoiSoanThao} — đối chiếu trực tiếp
 * {@code vn.com.tandan.xsp.services.vbdi.XuLyDuThao#TraLaiDuThaoChoLanhDaoKyDuyet()}/
 * {@code TraLaiDuThaoChoNguoiSoanThao()} (2 hàm cùng đọc {@code {id, iddt, opinion}} từ JSON body).
 */
@Data
public class TraLaiDuThaoRequest {

    /** Id văn bản đi đang xử lý (Domino: {@code id}) — bắt buộc. */
    @NotNull(message = "vbdiId không được để trống")
    private UUID vbdiId;

    /**
     * Id dự thảo cha (Domino: {@code iddt}) — TUỲ CHỌN, rỗng thì tự đọc {@code draft_id} trên
     * chính văn bản đi (đúng fallback Domino dòng 204-207/319-322).
     */
    private UUID draftId;

    /** Ý kiến văn thư (Domino: {@code opinion}) — rỗng thì tự sinh "Văn thư trả lại lúc &lt;giờ&gt;". */
    private String opinion;
}
