package com.td.application.vbden;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BL-210/211 (VBDen — Sổ văn bản đến) — Slice 9: tương thích {@code qlvb/vbden/GetSoVanBanDen}
 * (Domino thật, {@code GetSoVanBanDen.java}) — màn "Vào sổ văn bản đến". Đọc trực tiếp từ
 * {@code docs/views_domino/view_vanbanden_so.dxl} (export view thật của {@code vbden_so.nsf}).
 *
 * <p>Khác {@link com.td.application.viewquery.ViewQueryRequest} (dùng chung cho {@code GetView}) vì
 * có 2 field riêng biệt không view nào khác cần: {@code sort} (đảo chiều {@code document_number}/
 * {@code received_date} — DUY NHẤT view này có UI đảo chiều thật) và {@code iddanhmucso} (lọc theo 1
 * sổ cụ thể — tương đương Domino tự đổi {@code viewname} sang {@code sovanban_by_document_register_id}
 * khi field này có giá trị, xem {@code GetSoVanBanDenUseCase}).</p>
 */
@Data
public class GetSoVanBanDenRequest {

    /** {@code sovanban}, {@code sovanban_sx_ngayden}, {@code homqua}, {@code trongngay},
     * {@code trongthang}, {@code trongtuan} — xem {@code GetSoVanBanDenUseCase}. */
    private String viewname = "sovanban";

    private String items = "getall";

    private int pageNumber = 1;

    private int pageSize = 20;

    private String keyword;

    private List<String> columns = new ArrayList<>();

    /** {@code "document_number_up"}/{@code "document_number_down"}/{@code "received_date_up"}/
     * {@code "received_date_down"}/rỗng — xem {@code ColumnsDenVaoSoVanBan.jsx}. */
    private String sort;

    /** UUID (dạng chuỗi) của {@code DocumentRegister} cụ thể — rỗng = không lọc theo sổ. */
    private String iddanhmucso;

    /**
     * 09/10/2026 (port màn "In sổ văn bản đến", Domino {@code InSoVanBanDen.java}, viewname
     * {@code insovanban}): bộ lọc nâng cao FE {@code vbden/inso/CollapseVBden.jsx} —
     * {@code received_date} = mảng ["dd/MM/yyyy","dd/MM/yyyy"], {@code document_register_id}.
     */
    private Map<String, Object> search;
}
