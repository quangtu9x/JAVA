package com.td.application.simpki;

/**
 * 1 vị trí đặt tem/chữ ký NHÌN THẤY trên PDF — tọa độ PDF points, gốc DƯỚI-TRÁI trang, {@code page}
 * 1-based. Tên field khớp 1:1 với JSON payload mà TDPdfViewer xuất ra (xem
 * {@code TDPdfViewer/docs/05-he-toa-do-va-payload.md}) VÀ với {@code SignaturePlacement} phía
 * TDSimPki ({@code com.td.worker.pdf.SignaturePlacement}) — module này chỉ chuyển tiếp nguyên vẹn,
 * không quy đổi field/trục nào. Xem javadoc phía TDSimPki cho ý nghĩa đầy đủ của {@code type}/
 * {@code text}/{@code color} (ghi chú dạng chữ) so với {@code imageBase64} (viết tay/ảnh).
 */
public record SimPkiSignaturePlacement(
    int page,
    float x,
    float y,
    float width,
    float height,
    String imageBase64,
    String type,
    String text,
    String color) {
}
