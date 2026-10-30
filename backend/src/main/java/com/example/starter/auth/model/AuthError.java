package com.example.starter.auth.model;

import com.example.starter.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 인증 기능의 공개 오류 코드다. 공통 Problem Details 경계를 그대로 사용한다. */
public enum AuthError implements ErrorCode {
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    TOKEN_REVOKED(HttpStatus.UNAUTHORIZED),
    TOKEN_REFRESH_REUSED(HttpStatus.UNAUTHORIZED),
    ORIGIN_REJECTED(HttpStatus.FORBIDDEN),
    CONCURRENT_LOGIN_DENIED(HttpStatus.CONFLICT),
    PASSWORD_LOGIN_UNAVAILABLE(HttpStatus.CONFLICT),
    CURRENT_PASSWORD_INVALID(HttpStatus.BAD_REQUEST),
    PASSWORD_UNCHANGED(HttpStatus.BAD_REQUEST),
    AUTH_STATE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    /** 인증 실패의 HTTP 의미를 고정한다. */
    AuthError(HttpStatus status) {
        this.status = status;
    }

    /** 공통 경계에서 사용할 상태다. */
    @Override
    public HttpStatus status() {
        return status;
    }
}
