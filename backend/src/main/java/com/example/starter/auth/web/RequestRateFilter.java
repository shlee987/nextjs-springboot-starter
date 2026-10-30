package com.example.starter.auth.web;

import com.example.starter.auth.model.AuthContext;
import com.example.starter.auth.store.AuthStateStore;
import com.example.starter.auth.store.TokenSecrets;
import com.example.starter.common.web.ApiError;
import com.example.starter.common.web.ApiException;
import com.example.starter.common.web.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/** 일반 요청/CSRF 발급도 사용자 또는 직접 연결 IP의 공유 한도를 사용한다. */
public final class RequestRateFilter extends OncePerRequestFilter {
    private static final String APPLIED = RequestRateFilter.class.getName() + ".applied";
    private final AuthStateStore store;
    private final Clock clock;

    /** 선택한 상태 저장소 하나만 사용한다. */
    public RequestRateFilter(AuthStateStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /** forwarded 신원 헤더는 사용자/IP 판정에 사용하지 않는다. */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long actor = AuthContext.actor();

        if (actor != null) {
            MDC.put("actorUserId", actor.toString());
            request.setAttribute(RequestContext.ACTOR_ATTRIBUTE, actor);
        }

        String key = actor == null ? "anonymous:" + RequestContext.peer(request) : "user:" + actor;
        long retry =
                store.consume(
                        "REQUEST",
                        TokenSecrets.hash(key),
                        actor == null ? 60 : 120,
                        60_000,
                        clock.millis());

        if (retry > 0) {
            throw new ApiException(ApiError.RATE_LIMITED, retry);
        }

        request.setAttribute(APPLIED, Boolean.TRUE);

        if (actor != null) {
            if (!"HEAD".equals(request.getMethod())) {
                store.touch(AuthContext.require().sid(), clock.millis());
            }
        }

        chain.doFilter(request, response);
    }

    /** JWT/상태 검증에서 조기 거부된 익명 요청도 같은 한도를 한 번 소비한다. */
    public static void limitAnonymous(
            AuthStateStore store, Clock clock, HttpServletRequest request) {
        if (Boolean.TRUE.equals(request.getAttribute(APPLIED))) {
            return;
        }

        String key = "anonymous:" + RequestContext.peer(request);
        long retry = store.consume("REQUEST", TokenSecrets.hash(key), 60, 60_000, clock.millis());
        request.setAttribute(APPLIED, Boolean.TRUE);

        if (retry > 0) {
            throw new ApiException(ApiError.RATE_LIMITED, retry);
        }
    }
}
