package com.td.infrastructure.security;

import org.springframework.security.core.AuthenticationException;

/**
 * Ném ra khi header {@code Tdtoken} có mặt nhưng verify thất bại (sai chữ ký, hết hạn, parse lỗi).
 * Message giữ nguyên "Sai token" để tương thích với luồng tự động refresh token phía FE hiện tại
 * (FE so khớp chuỗi {@code response.data.message === "Sai token"} để refresh thầm lặng).
 */
public class TdTokenAuthenticationException extends AuthenticationException {

    public TdTokenAuthenticationException(String message) {
        super(message);
    }

    public TdTokenAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
