package com.example.starter.auth.web;

import com.example.starter.auth.model.AuthError;
import com.example.starter.common.web.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/** 상태 변경은 정확한 public origin 또는 Referer origin을 요구한다. */
public final class OriginFilter extends OncePerRequestFilter {
    private final String origin;

    /** 시작 시 검증한 단일 public origin을 사용한다. */
    public OriginFilter(String origin) {
        this.origin = origin;
    }

    /** Origin이 존재하면 Referer로 우회하지 않는다. */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!Set.of("GET", "HEAD", "OPTIONS", "TRACE").contains(request.getMethod())) {
            String supplied = request.getHeader("Origin");

            if (supplied == null) {
                supplied = refererOrigin(request.getHeader("Referer"));
            }

            if (!origin.equals(supplied)) {
                throw new ApiException(AuthError.ORIGIN_REJECTED);
            }
        }

        chain.doFilter(request, response);
    }

    /** Referer의 경로·쿼리는 검증 비교나 로그에 사용하지 않는다. */
    private String refererOrigin(String value) {
        try {
            URI uri = URI.create(value);

            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                return null;
            }

            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), null, null, null)
                    .toString();
        } catch (Exception exception) {
            return null;
        }
    }
}
