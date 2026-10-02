package com.td.application.simpki;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Yêu cầu ký 1 PDF qua SIM-PKI (TDSimPki) — {@code pdfBytes} là nội dung PDF THẬT (host tự tải/convert
 * trước khi gọi, use case này KHÔNG biết gì về nguồn gốc tệp: Java/MinIO, Domino, hay ứng dụng khác
 * hoàn toàn — đúng tinh thần "generic contract" của TDSimPki, xem
 * {@code TDSimPki/docs/19-backend-webapi-pdf-checklist.md} §3.1).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitSimPkiSignatureRequest {
    private byte[] pdfBytes;
    private String fileName;
    private String msisdn;
    private String displayMessage;
    private List<SimPkiSignaturePlacement> signatures;
}
