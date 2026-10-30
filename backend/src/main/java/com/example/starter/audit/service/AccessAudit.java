package com.example.starter.audit.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 서비스 경계에 고정 resource와 감사 실패 정책을 지정한다. SpEL은 지원하지 않는다. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AccessAudit {
    /** 서버에 등록된 기능만 지정한다. */
    Resource resource();

    /** 검색/조회/변경 행위를 명시한다. */
    Action action();

    /** REQUIRED 실패는 응답을 차단한다. */
    Policy policy() default Policy.BEST_EFFORT;

    /** 권한 거부 경계에서도 같은 registry를 사용한다. */
    enum Resource {
        FILE,
        USER_LIST,
        USER_DETAIL,
        DEPARTMENT,
        ACCESS_AUDIT,
        SECURITY_AUDIT
    }

    /** 배포 코드로 고정된 행위다. */
    enum Action {
        READ,
        SEARCH,
        CREATE,
        UPDATE,
        DELETE,
        EXPORT
    }

    /** 필수 저장과 결과 보존 정책이다. */
    enum Policy {
        REQUIRED,
        BEST_EFFORT
    }
}
