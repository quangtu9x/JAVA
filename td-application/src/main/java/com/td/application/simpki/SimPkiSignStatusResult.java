package com.td.application.simpki;

/** Trạng thái 1 lượt ký SIM-PKI — {@code status}: READY|SIGNING|SIGNED|FAILED (khớp TDSimPki). */
public record SimPkiSignStatusResult(String simPkiFileId, String status, String errorDetail, boolean signed) {
}
