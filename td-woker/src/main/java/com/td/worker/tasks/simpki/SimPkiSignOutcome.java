package com.td.worker.tasks.simpki;

/**
 * Kết quả gọi cổng ký số SIM-PKI. Tách khỏi {@code UnitResult} (Worker Core) vì đây là kết quả
 * NGHIỆP VỤ riêng của module này (mang thêm chữ ký/mã trạng thái/mã xác thực của MSSP) —
 * {@link SimPkiSignTaskHandler} là nơi DUY NHẤT chuyển đổi nó sang {@code UnitResult}.
 */
public record SimPkiSignOutcome(
        boolean success,
        String signatureBase64,
        String transId,
        String verificationCode,
        Long statusCode,
        String errorCode,
        String errorDesc) {

    public static SimPkiSignOutcome ok(String signatureBase64, String transId, String verificationCode, Long statusCode) {
        return new SimPkiSignOutcome(true, signatureBase64, transId, verificationCode, statusCode, null, null);
    }

    public static SimPkiSignOutcome failed(String errorCode, String errorDesc) {
        return new SimPkiSignOutcome(false, null, null, null, null, errorCode, errorDesc);
    }
}
