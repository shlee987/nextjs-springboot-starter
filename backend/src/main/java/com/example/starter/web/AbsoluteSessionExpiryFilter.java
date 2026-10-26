package com.example.starter.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import org.springframework.web.filter.OncePerRequestFilter;

/** Redis TTL과 별개로 세션 생성 후 8시간 상한을 서버에서 강제한다. */
public final class AbsoluteSessionExpiryFilter extends OncePerRequestFilter {
    /** 기존 세션만 확인하고 절대 수명을 넘으면 공유 저장소에서 폐기한다. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);

        if (session != null && System.currentTimeMillis() - session.getCreationTime()
                >= Duration.ofHours(8).toMillis()) {
            session.invalidate();
        }

        chain.doFilter(request, response);
    }
}
