package com.td.application.viewquery;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BL-102 (View Query Engine): tương thích {@code qlvb/view/query} — {@code {viewname, keys, items,
 * pageNumber, pageSize}}.
 */
@Data
public class ViewQueryRequest {

    @NotBlank(message = "viewname khong duoc de trong")
    private String viewname;

    private List<String> keys = new ArrayList<>();

    private String items = "getall";

    // 1-based, khớp quy ước legacy Domino (TDUlties: startIdx = (pageNumber - 1) * pageSize)
    private int pageNumber = 1;

    private int pageSize = 20;

    /**
     * Slice 7 (tìm kiếm/lọc): tương đương {@code keyword} Domino ({@code GetSoVanBanDen}/
     * {@code getEntriesView} — FTSearch trên các field trong {@link #columns}). Rỗng/null = không
     * lọc — giữ nguyên hành vi cũ (KHÔNG bắt buộc FE phải gửi field này).
     */
    private String keyword;

    /**
     * Danh sách field động cần tìm {@code keyword} — FE luôn gửi cố định theo module (vd.
     * {@code columnsSearchVBden} = document_number, code, document_type, unit_in_charge, subject).
     * Rỗng = bỏ qua {@code keyword} dù có giá trị (tránh đoán field mặc định thay FE).
     */
    private List<String> columns = new ArrayList<>();

    /**
     * 31/08/2026 (port "toanbovanban" — màn "Toàn bộ văn bản" mặc định, xem
     * docs/analysis/02-checklist-port-vbden-con-lai.md): khoảng ngày cho bộ lọc RangePicker "khoảng
     * ngày nhận văn bản" ({@code received_date}). Chuỗi {@code dd/MM/yyyy} (KHÔNG phải ISO — xem
     * bugfix D.35 cùng ngày), khớp đúng định dạng {@code date_to_dd_mm_yyyy()} phía FE luôn dùng cho
     * field này. Rỗng/null = không lọc theo ngày (giữ nguyên hành vi hiện tại của mọi view khác).
     */
    private String receivedDateFrom;
    private String receivedDateTo;

    /**
     * 01/09/2026 ("tìm kiếm nâng cao" — {@code CollapseVBden.jsx}, xem
     * docs/analysis/02-checklist-port-vbden-con-lai.md mục 1.3/Phần 4): truyền THẲNG object
     * {@code dataAdvanceSearchAll} FE gửi (đúng cấu trúc {@code search} Domino cũ) — CHỈ 5 field đã
     * xác nhận cho {@code theodoi}/{@code datheodoi} được xử lý ({@link
     * com.td.application.viewquery.ViewQueryService#buildAdvancedSearchFilter}): {@code received_date}
     * ({@code [dd/MM/yyyy, dd/MM/yyyy]} — khoảng ngày), {@code status} (giá trị đơn — cột tĩnh),
     * {@code unit_in_charge}/{@code unit_process} (giá trị đơn — field động, containment),
     * {@code user_process} (chuỗi {@code ";"}-nối nhiều username — field động, containment OR).
     * Field lạ/không nhận diện được BỎ QUA (không suy đoán thêm ý nghĩa). Rỗng/null = không lọc thêm,
     * giữ nguyên hành vi hiện tại.
     */
    private Map<String, Object> search = new LinkedHashMap<>();

    /**
     * 02/10/2026 (route {@code vbden/xuly/theodoi} — nút sắp xếp cột "Ngày đến"): {@code "asc"}/
     * {@code "desc"} theo {@code received_date}. Rỗng/null = thứ tự mặc định của view. Hiện chỉ
     * {@code theodoi}/{@code datheodoi} đọc field này.
     */
    private String sortOrder;
}
