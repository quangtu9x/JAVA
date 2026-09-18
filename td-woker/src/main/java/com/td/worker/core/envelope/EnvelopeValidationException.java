package com.td.worker.core.envelope;

/**
 * Envelope không hợp lệ (thiếu trường bắt buộc, sai định dạng JSON, không có unit...).
 * Message lỗi LOẠI NÀY phải rơi vào DLQ (không retry vô ích).
 */
public class EnvelopeValidationException extends RuntimeException {

    public EnvelopeValidationException(String message) {
        super(message);
    }

    public EnvelopeValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}