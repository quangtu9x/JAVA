package com.td.infrastructure.simpki;

/** Lỗi gọi TDSimPki (chưa cấu hình, HTTP lỗi, timeout...) — bắt ở use case impl, đổi thành {@code Result.failure}. */
public class TdSimPkiException extends RuntimeException {
    public TdSimPkiException(String message) {
        super(message);
    }

    public TdSimPkiException(String message, Throwable cause) {
        super(message, cause);
    }
}
