package com.example.starter.file.model;

import com.example.starter.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 파일 입력과 저장 실패를 원문 경로 없이 구분한다. */
public enum FileError implements ErrorCode {
    FILE_NAME_INVALID(HttpStatus.BAD_REQUEST),
    FILE_TYPE_UNSUPPORTED(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    FILE_CONTENT_INVALID(HttpStatus.BAD_REQUEST),
    FILE_QUOTA_EXCEEDED(HttpStatus.CONFLICT),
    FILE_PROCESSING_BUSY(HttpStatus.TOO_MANY_REQUESTS),
    FILE_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    FILE_METADATA_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);
    private final HttpStatus status;

    /** 공개 상태만 보관한다. */
    FileError(HttpStatus status) {
        this.status = status;
    }

    /** 공통 오류 응답의 상태를 제공한다. */
    public HttpStatus status() {
        return status;
    }
}
