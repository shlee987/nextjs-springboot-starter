package com.example.starter.auth.model;

import com.example.starter.common.web.ApiError;
import com.example.starter.common.web.ApiException;
import org.springframework.security.core.context.SecurityContextHolder;

/** HTTP 입력 대신 검증된 request SecurityContext에서 주체를 읽는다. */
public final class AuthContext {
    /** 정적 request 접근 도구의 인스턴스 생성을 막는다. */
    private AuthContext() {}

    /** 익명 요청과 시스템 실행에는 가짜 userId를 만들지 않는다. */
    public static Long actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        return authentication != null && authentication.getPrincipal() instanceof AuthLogin login
                ? login.userId()
                : null;
    }

    /** 보호 업무가 검증된 sid를 필요로 할 때만 반환한다. */
    public static AuthLogin require() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof AuthLogin)) {
            throw new ApiException(ApiError.UNAUTHENTICATED);
        }

        return (AuthLogin) authentication.getPrincipal();
    }
}
