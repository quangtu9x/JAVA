package com.td.application.simpki;

import java.time.Instant;

/**
 * Thông tin chứng thư số (X.509) của thuê bao SIM-PKI — khớp 1:1 response của TDSimPki
 * {@code POST /api/simpki/certificates} (xem {@code TDSimPki/docs/23-simpki-certificate-api.md}).
 */
public record SimPkiCertificateResult(
    String msisdn,
    String certificateBase64,
    String certificateChainDerBase64,
    String serialNumber,
    String subjectDn,
    String issuerDn,
    Instant notBefore,
    Instant notAfter,
    String signatureAlgorithm,
    String publicKeyAlgorithm,
    Integer publicKeySizeBits,
    Integer certificateChainLength) {
}
