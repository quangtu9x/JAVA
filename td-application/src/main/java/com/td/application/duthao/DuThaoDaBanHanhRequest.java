package com.td.application.duthao;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026 ("port lại XuLyDuThao.java, class này có các chức năng tương ứng ở
 * button của văn bản đi"): request cho {@code POST /api/v1/duthao/DaBanHanh} — tương đương Ý NGHĨA
 * với {@code qlvb/vbdi/dabanhanhduthao?databasetype=duthao&id=<id>&vbdiid=<vbdiid>&vbdicode=<code>}
 * (Domino, đối chiếu trực tiếp {@code vn.com.tandan.xsp.services.vbdi.XuLyDuThao#
 * ChuyenTrangThaiDuThaoDaBanHanh()}) — đánh dấu dự thảo đã hoàn tất, văn bản đi sinh ra từ nó ĐÃ
 * BAN HÀNH chính thức với số/ký hiệu cụ thể.
 */
@Data
public class DuThaoDaBanHanhRequest {

    /** Id dự thảo cha cần cập nhật — bắt buộc. */
    @NotNull(message = "id không được để trống")
    private UUID id;

    /** Id văn bản đi đã ban hành — bắt buộc. */
    @NotNull(message = "vbdiId không được để trống")
    private UUID vbdiId;

    /** Số/ký hiệu chính thức của văn bản đi (vd "123/QĐ-UBND") — tuỳ chọn. */
    private String vbdiCode;
}
