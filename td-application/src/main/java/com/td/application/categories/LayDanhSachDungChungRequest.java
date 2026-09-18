package com.td.application.categories;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * BL-118 (tiếp, 20/08/2026): request cho {@code POST /api/v1/danhmuc/LayDanhSachDungChung} — tương
 * thích {@code qlvb/danhmuc/LayDanhSachDungChung} (Domino thật). Dữ liệu dùng chung với
 * {@code CategoriesController} (bảng {@code categories}, BL-113) — {@code keys[0]} chính là
 * {@code Category.form} (vd {@code document_type}/{@code sector}/{@code priority}/{@code method}).
 * {@code viewname}/{@code items} nhận cho đúng hợp đồng cũ nhưng KHÔNG dùng (trừ 3 viewname tổ
 * chức/người dùng — xem {@code LegacyDanhMucController#layDanhSachDungChung}) — nhánh Category mặc
 * định luôn trả về 1 tập field cố định đủ dùng ({@code id}/{@code name}/{@code sort_order}/
 * {@code short_name}/{@code content}/{@code notification}), đơn giản hơn cơ chế "items" tuỳ ý phía
 * Domino.
 *
 * <p>Bổ sung 09/09/2026 (cutover Footer.tsx/Banner.tsx/ListItemHDSD.tsx — form {@code footer}/
 * {@code banner}/{@code help}): {@code image=true} yêu cầu nhúng thêm {@code files[].content}
 * (base64 data-URI của tệp đính kèm Category, qua {@code MinioService} — CHỈ tải khi thực sự cần,
 * tránh tốn I/O cho 30+ call site "danh mục theo form" khác không dùng field này).
 * {@code search.isactive} ("true"/"false", theo đúng Domino gốc) lọc {@code Category.isActive} —
 * Banner.tsx dùng để chỉ lấy banner đang bật.
 */
@Data
public class LayDanhSachDungChungRequest {
    private String viewname;
    private List<String> keys;
    private String keyword;
    private String items;
    private Integer pageNumber;
    private Integer pageSize;
    private Boolean image;
    private Map<String, Object> search;
}
