package com.example.starter.audit.model;

import com.example.starter.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 감사 저장 실패의 공개 코드는 인증 기능과 독립적으로 관리한다. */
public enum AuditError implements ErrorCode {
    AUDIT_UNAVAILABLE;

    /** 민감 결과를 반환하지 않고 일시 장애를 표시한다. */
    @Override
    public HttpStatus status() {
        return HttpStatus.SERVICE_UNAVAILABLE;
    }
}
