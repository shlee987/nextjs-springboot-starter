package com.example.starter.web;

/** 승인한 실패 코드와 내부 진단 원인을 분리해 전달한다. */
public final class ApiException extends RuntimeException {
    private final ApiError error;

    /** 등록된 오류만으로 업무 실패를 전달한다. */
    public ApiException(ApiError error) {
        this(error, null);
    }

    /** 원인을 보존하되 공개 응답에 원문을 사용하지 않는다. */
    public ApiException(ApiError error, Throwable cause) {
        super(error.name(), cause);
        this.error = error;
    }

    /** 공통 처리기가 사용할 승인 코드를 반환한다. */
    public ApiError error() {
        return error;
    }
}
