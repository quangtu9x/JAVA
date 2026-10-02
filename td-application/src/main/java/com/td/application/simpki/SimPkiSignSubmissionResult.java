package com.td.application.simpki;

/**
 * Kết quả nộp yêu cầu ký — {@code simPkiFileId} là ID bản ghi PDF phía TDSimPki (KHÁC hoàn toàn
 * {@code fileId}/{@code documentId} của hệ thống này) — dùng để gọi
 * {@link GetSimPkiSignStatusUseCase}/{@link DownloadSignedPdfUseCase} sau đó.
 */
public record SimPkiSignSubmissionResult(String simPkiFileId, String status) {
}
