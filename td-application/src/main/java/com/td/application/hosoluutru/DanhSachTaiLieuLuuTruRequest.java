package com.td.application.hosoluutru;

import lombok.Data;

/**
 * 30/10/2026: request cho {@code POST /api/v1/hosoluutru/DanhSachVanBan} và {@code /QuaTrinh} — thay
 * {@code qlvb/view/query} view {@code vanban_by_hoso_id}/{@code vwQuaTrinh*_Document_Profiles_ID}.
 */
@Data
public class DanhSachTaiLieuLuuTruRequest {

    private String hosoId;

    /** Chỉ dùng cho /QuaTrinh: "chuyenltls" (vết LTLS) hoặc "quatrinh" (vết khác). */
    private String loai;

    private String keyword;

    private Integer pageNumber;

    private Integer pageSize;
}
