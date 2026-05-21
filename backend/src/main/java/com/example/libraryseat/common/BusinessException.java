package com.example.libraryseat.common;

import org.springframework.http.HttpStatus;

/**
 * 业务异常，由 GlobalExceptionHandler 映射为对应 HTTP 状态码。
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    private BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, message);
    }

    public static BusinessException internalError(String message) {
        return new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}
