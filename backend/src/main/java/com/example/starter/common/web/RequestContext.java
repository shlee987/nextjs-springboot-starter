package com.example.starter.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 직접 peer만 정규화하며 forwarded 신원 헤더는 사용하지 않는다. */
public record RequestContext(String traceId, String clientIp, String serverId) {
    private static final ThreadLocal<RequestContext> CURRENT = new ThreadLocal<>();

    public static final String ATTRIBUTE = RequestContext.class.getName();
    public static final String ACTOR_ATTRIBUTE = ATTRIBUTE + ".actor";

    /** Dispatcher 이전 거부 이력에도 최외곽 필터의 정규화 문맥을 제공한다. */
    static void bind(RequestContext context) {
        CURRENT.set(context);
    }

    /** pool thread가 다음 요청의 peer를 재사용하지 않게 한다. */
    static void clear() {
        CURRENT.remove();
    }

    /** HTTP 문맥이 없는 SYSTEM과 유효하지 않은 peer는 NULL로 영속한다. */
    public static String currentPeer() {
        RequestContext context = CURRENT.get();
        if (context == null
                && RequestContextHolder.getRequestAttributes()
                        instanceof ServletRequestAttributes attributes) {
            Object stored = attributes.getRequest().getAttribute(ATTRIBUTE);
            if (stored instanceof RequestContext requestContext) {
                context = requestContext;
            }
        }
        return context == null || "UNKNOWN".equals(context.clientIp()) ? null : context.clientIp();
    }

    /** 진단 문자열에는 직접 peer를 포함하지 않는다. */
    @Override
    public String toString() {
        return "RequestContext[redacted]";
    }

    /** container가 제공한 숫자 IP만 해석하여 DNS 조회를 발생시키지 않는다. */
    public static String peer(HttpServletRequest request) {
        String peer = request.getRemoteAddr();

        if (peer == null || !peer.matches("[0-9a-fA-F:.]+")) {
            return "UNKNOWN";
        }

        try {
            return InetAddress.getByName(peer).getHostAddress();
        } catch (Exception exception) {
            return "UNKNOWN";
        }
    }
}
