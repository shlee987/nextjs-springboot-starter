package com.example.starter.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 요청 attribute에 서버가 생성한 UUID를 유지한다. 클라이언트 헤더는 채택하지 않는다. */
public final class RequestTrace {
    private static final String ATTRIBUTE = RequestTrace.class.getName();

    /** 요청 경계 전용 도구의 생성을 막는다. */
    private RequestTrace() {}

    /** 같은 요청과 error dispatch에서 동일한 식별자를 반환한다. */
    public static String get(HttpServletRequest request) {
        Object existing = request.getAttribute(ATTRIBUTE);

        if (existing instanceof String traceId) {
            return traceId;
        }

        String traceId = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, traceId);

        return traceId;
    }

    /** MVC 요청에 연결된 식별자를 읽는다. 요청 밖에서 성공 응답 생성은 거부한다. */
    public static String current() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            String trace = MDC.get("traceId");

            return trace == null ? UUID.randomUUID().toString() : trace;
        }

        return get(attributes.getRequest());
    }
}
