package com.td.application.simpki;

/** PDF đã nhúng chữ ký USB Token (CMS detached) — xem {@link CompleteExternalPdfSignatureUseCase}. */
public record ExternalPdfSignatureResult(String fileBase64, String fileName) {
}
