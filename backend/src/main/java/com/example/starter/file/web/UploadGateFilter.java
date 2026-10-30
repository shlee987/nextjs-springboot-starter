package com.example.starter.file.web;

import com.example.starter.auth.model.AuthContext;
import com.example.starter.auth.store.AuthStateStore;
import com.example.starter.auth.store.TokenSecrets;
import com.example.starter.common.web.ApiError;
import com.example.starter.common.web.ApiException;
import com.example.starter.common.web.RequestBodyPolicy;
import com.example.starter.file.service.UploadAdmission;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.web.filter.OncePerRequestFilter;

/** 인증·Origin/CSRF·인가 후, multipart parser보다 앞에서 상한을 적용한다. */
public final class UploadGateFilter extends OncePerRequestFilter {
    private final UploadAdmission admission;
    private final AuthStateStore states;
    private final Clock clock;

    /** Servlet filter로 중복 등록하지 않고 Security chain에서만 사용한다. */
    public UploadGateFilter(UploadAdmission admission, AuthStateStore states, Clock clock) {
        this.admission = admission;
        this.states = states;
        this.clock = clock;
    }

    /** 소유자10회/분과 노드2병렬을 넘으면 기다리지 않고429로 처리한다. */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!RequestBodyPolicy.upload(request)) {
            chain.doFilter(request, response);

            return;
        }

        long user = AuthContext.require().userId();
        long retry =
                states.consume(
                        "REQUEST",
                        TokenSecrets.hash("file-upload:" + user),
                        10,
                        60000,
                        clock.millis());

        if (retry > 0) {
            throw new ApiException(ApiError.RATE_LIMITED, retry);
        }

        try (var permit = admission.enter()) {
            chain.doFilter(request, response);
        }
    }
}
