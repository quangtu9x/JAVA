package com.td.application.vbden;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 10/10/2026 — màn "Báo cáo tổng hợp theo đơn vị - cá nhân" ({@code vbden/baocaoinso/baocaotheophong}),
 * thay 2 API Domino {@code qlvb/vbcddh/danhsachdonvithuchien} + {@code qlvb/vbden/TimKiemVanBanDen}.
 */
@Data
public class BaoCaoTheoPhongVbdenRequest {

    /** Bộ lọc nâng cao CollapseVBden — dùng {@code received_date} = ["dd/MM/yyyy","dd/MM/yyyy"]. */
    private Map<String, Object> search = new LinkedHashMap<>();

    private String keyword;

    /**
     * Gốc cây báo cáo (agency_id / unit_id / department_id theo vai trò văn thư, cùng quy tắc
     * {@code CollapseVBden.jsx#resolveUnitProcessScopeRootId}). Chỉ chọn được trong cây đã cắt theo cơ
     * quan của người dùng — không tìm thấy thì trả cả cây đó.
     */
    private String rootId;
}
