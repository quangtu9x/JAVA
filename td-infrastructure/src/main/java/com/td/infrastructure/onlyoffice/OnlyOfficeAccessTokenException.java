package com.td.infrastructure.onlyoffice;

/** Token phạm vi hẹp (content/callback) không hợp lệ, hết hạn, hoặc sai {@code purpose}. */
public class OnlyOfficeAccessTokenException extends RuntimeException {

    public OnlyOfficeAccessTokenException(String message) {
        super(message);
    }

    public OnlyOfficeAccessTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
