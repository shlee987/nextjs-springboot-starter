package com.example.starter.common.web;

import com.example.starter.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 공개 오류 코드와 HTTP 상태의 단일 등록부다. 원문 예외 문구는 보관하지 않는다. */
public enum ApiError implements ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    CSRF_TOKEN_MISSING(HttpStatus.FORBIDDEN),
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    CONFLICT(HttpStatus.CONFLICT),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    /** 오류의 공개 HTTP 상태를 등록한다. */
    ApiError(HttpStatus status) {
        this.status = status;
    }

    /** 실제 응답에 사용할 상태를 반환한다. */
    public HttpStatus status() {
        return status;
    }

    /** 서버 메시지 자원의 키를 반환한다. */
    public String messageKey() {
        return "error." + name();
    }

    /** fallback의 HTTP 의미를 보존하며 등록한 오류로 정규화한다. */
    public static ApiError fromStatus(int status) {
        return switch (status) {
            case 400 -> MALFORMED_REQUEST;
            case 401 -> UNAUTHENTICATED;
            case 403 -> ACCESS_DENIED;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 409 -> CONFLICT;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 429 -> RATE_LIMITED;
            case 503 -> DEPENDENCY_UNAVAILABLE;
            default -> INTERNAL_ERROR;
        };
    }
}
