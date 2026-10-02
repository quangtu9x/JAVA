package com.td.application.simpki;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Yêu cầu định vị vị trí chữ ký trên 1 PDF (bytes gửi thẳng, không lưu ở TDSimPki). Cung cấp
 * {@code signerName} (tên người ký — use case tự dựng regex an toàn, mặc định ALL_PAGES: mỗi trang có
 * tên trả 1 vị trí = lần xuất hiện cuối trong trang, tức khối chữ ký) HOẶC {@code signatureLocators} (regex tuỳ ý, chuyển tiếp
 * nguyên vẹn — TDSimPki tự chặn regex nguy hiểm). Có cả hai thì regex tên người ký đứng đầu.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocateSimPkiSignaturesRequest {
    private byte[] pdfBytes;
    private String fileName;
    private String signerName;
    private List<String> signatureLocators;
    /** FIRST | LAST | ALL_PAGES — bỏ trống: ALL_PAGES (mỗi trang có tên = 1 vị trí) nếu có {@code signerName}, ngược lại FIRST. */
    private String occurrence;
    /**
     * (30/09/2026) Khoảng trống tối thiểu (pt) phía trên vùng chữ khớp để coi là "vị trí phù hợp đặt chữ
     * ký" — bỏ trống: 36pt nếu có {@code signerName} (kèm điều kiện tên đứng riêng dòng), ngược lại không
     * xét. Truyền 0 để tắt điều kiện khoảng trống.
     */
    private Float minClearanceAbovePt;
}
