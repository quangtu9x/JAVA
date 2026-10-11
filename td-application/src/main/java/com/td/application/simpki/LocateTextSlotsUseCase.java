package com.td.application.simpki;

import com.td.application.common.models.Result;

import java.util.Map;

/**
 * 08/10/2026 (ký USB Token mới ở Văn bản đi — các lựa chọn "chèn số ký hiệu / ngày tháng ban hành..." như
 * luồng ký USB cũ): chuyển tiếp sang TDSimPki {@code POST /api/pdf/locate-text-slots} (stateless) — định vị
 * "ô trống" (vd khoảng trắng sau "Số:", giữa "ngày … tháng … năm") trong PDF. {@code slotsJson} = chuỗi JSON
 * mảng {@code [{id, regex, occurrence?}]}. Trả nguyên {@code {pages:[...], slots:[...]}} của TDSimPki; không
 * khớp không phải lỗi ({@code found=false}).
 */
public interface LocateTextSlotsUseCase {
    Result<Map<String, Object>> execute(byte[] pdfBytes, String fileName, String slotsJson);
}
