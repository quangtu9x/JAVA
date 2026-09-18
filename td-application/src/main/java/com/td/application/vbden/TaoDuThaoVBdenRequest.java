package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * N19 (04/09/2026 — "Tạo mới văn bản dự thảo", nút "Tạo dự thảo"
 * {@code VBDenButtonActionXuLy.jsx#btnTaoDuThao}, lưu ở {@code ChiTietPage.jsx} module duthao): request
 * cho {@code POST /api/v1/vbden/TaoDuThao} — tương thích 1 PHẦN {@code qlvb/vbden/CapNhatVanBanDen?
 * action=taoduthao} ({@code CapNhatVanBanDen.java} thật, hàm {@code taoduthao_hoso_phieutrinh()} — xem
 * lý do CHỈ port 1 phần trong javadoc {@link TaoDuThaoVBdenUseCase}).
 *
 * <p>LƯU Ý TÊN: KHÁC {@code com.td.application.duthao.TaoDuThaoUseCase}/{@code TaoDuThaoRequest} (tạo
 * BẢN THÂN tài liệu dự thảo, đã port trước — {@code POST /api/v1/duthao/TaoDuThao}). Class này port
 * hành động THỨ HAI, riêng biệt: đánh dấu "ai đó đang soạn dự thảo trả lời" trên CHÍNH văn bản đến gốc
 * và ghi vết {@code vbden_res} — 2 request khác nhau, FE gọi tuần tự (xem
 * {@code ChiTietPage.jsx} dòng ~1075-1113: gọi {@code taoDuThao()} trước để tạo tài liệu dự thảo, xong
 * mới gọi tới class này qua {@code capnhatVBden(...)}).
 */
@Data
public class TaoDuThaoVBdenRequest {

    @NotNull(message = "parentid khong duoc de trong")
    private UUID parentid;

    /** Ý kiến — FE ({@code capnhatVBden}) gửi field {@code opinion}, không bắt buộc. */
    private String opinion;

    /** Tên hiển thị người thực hiện — chỉ để hiển thị (giống {@link CapNhatKetQuaXuLyRequest#getCreatorName()}). */
    private String creatorName;

    /** Chức vụ hiển thị — chỉ để hiển thị. */
    private String position;

    /**
     * {@code res_obj} — tham chiếu tới văn bản dự thảo vừa tạo/gán ({@code id}/{@code dbpath}/
     * {@code code}, xem {@code capnhatVBden()} trong {@code global.jsx}). Chỉ LƯU LẠI trên bản ghi
     * {@code vbden_res} (hiển thị lại ở tab "Quá trình xử lý") — KHÔNG xử lý tiếp như
     * {@code processResObj()} thật (khác {@code vanthuketthucxuly}/{@code ketthucxuly}, xem javadoc
     * lớp use case).
     */
    private UUID resObjId;

    private String resObjDbpath;

    private String resObjCode;
}
