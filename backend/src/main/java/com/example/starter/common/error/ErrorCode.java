package com.example.starter.common.error;

import org.springframework.http.HttpStatus;

/** 기능 enum이 동일한 오류 경계를 사용하도록 공개 code·상태·서버 메시지 키를 연결한다. */
public interface ErrorCode {
    /** enum의 고정 공개 이름이다. */
    String name();

    /** HTTP 의미를 공통 경계에 전달한다. */
    HttpStatus status();

    /** 메시지 자원 키를 반환하며 API detail로 복사하지 않는다. */
    default String messageKey() {
        return "error." + name();
    }
}
