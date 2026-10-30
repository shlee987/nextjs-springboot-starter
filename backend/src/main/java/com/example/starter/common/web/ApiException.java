package com.example.starter.common.web;

import com.example.starter.common.error.ErrorCode;

/** 승인한 실패 코드와 내부 진단 원인을 분리해 전달한다. */
public final class ApiException extends RuntimeException {
    private final ErrorCode error;
    private final long retryAfterSeconds;

    /** 등록된 오류만으로 업무 실패를 전달한다. */
    public ApiException(ErrorCode error) {
        this(error, null);
    }

    /** 원인을 보존하되 공개 응답에 원문을 사용하지 않는다. */
    public ApiException(ErrorCode error, Throwable cause) {
        super(error.name(), cause);
        this.error = error;
        this.retryAfterSeconds = 0;
    }

    /** 한도 오류에서만 안전한 정수 Retry-After를 전달한다. */
    public ApiException(ErrorCode error, long retryAfterSeconds) {
        super(error.name());
        this.error = error;
        this.retryAfterSeconds = Math.max(0, retryAfterSeconds);
    }

    /** 응답 본문을 늘리지 않고 프로토콜 헤더에 사용할 초를 반환한다. */
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    /** 공통 처리기가 사용할 승인 코드를 반환한다. */
    public ErrorCode error() {
        return error;
    }
}
