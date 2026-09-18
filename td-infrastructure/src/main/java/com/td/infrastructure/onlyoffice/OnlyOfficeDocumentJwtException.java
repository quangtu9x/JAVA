package com.td.infrastructure.onlyoffice;

/** JWT do chính OnlyOffice Document Server ký (config token / callback token) không hợp lệ. */
public class OnlyOfficeDocumentJwtException extends RuntimeException {

    public OnlyOfficeDocumentJwtException(String message) {
        super(message);
    }

    public OnlyOfficeDocumentJwtException(String message, Throwable cause) {
        super(message, cause);
    }
}
