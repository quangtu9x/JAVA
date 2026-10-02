package com.td.application.simpki;

/**
 * 1 kết quả định vị (xem {@link LocateSimPkiSignaturesUseCase}) — tọa độ PDF points, gốc DƯỚI-TRÁI
 * trang, {@code page} 1-based: CÙNG hệ với {@link SimPkiSignaturePlacement} và với
 * {@code PdfSignatureLocatorResponse} phía TDSimPki (chuyển tiếp nguyên vẹn, không quy đổi). Hộp là
 * VÙNG CHỮ KHỚP (vd tên người ký), không phải hộp tem — caller tự quyết đặt tem ở đâu so với hộp này
 * (TDPdfViewer: kênh {@code place-signatures} position {@code above}).
 */
public record SimPkiSignatureLocation(
    String regex,
    String matchedText,
    int page,
    float x,
    float y,
    float width,
    float height,
    /** (30/09/2026) Khoảng trống dọc (pt) phía trên vùng chữ khớp tới chữ gần nhất phía trên. */
    Float clearanceAbove,
    /** (30/09/2026) true nếu vùng chữ khớp đứng riêng trên dòng (không có chữ khác sát bên). */
    Boolean aloneOnLine) {
}
