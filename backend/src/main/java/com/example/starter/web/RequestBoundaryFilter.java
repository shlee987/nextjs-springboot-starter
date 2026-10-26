package com.example.starter.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Session·Security를 포함한 후속 필터의 실패를 같은 공개 계약으로 처리한다. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class RequestBoundaryFilter extends OncePerRequestFilter {
    private final ProblemResponses problems;

    /** Servlet 체인의 최외곽에 공통 오류 출력을 연결한다. */
    public RequestBoundaryFilter(ProblemResponses problems) {
        this.problems = problems;
    }

    /** 요청 MDC를 정리하며 조기 거부부터 공통 헤더와 안전한 오류 JSON을 적용한다. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String traceId = RequestTrace.get(request);
        response.setHeader("X-Request-ID", traceId);
        MDC.put("traceId", traceId);

        try {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Security-Policy",
                    "default-src 'none'; base-uri 'none'; "
                            + "frame-ancestors 'none'; form-action 'none'");

            if (request.getContentLengthLong() > BoundedBodyRequest.MAX_BYTES) {
                throw new ApiException(ApiError.PAYLOAD_TOO_LARGE);
            }

            chain.doFilter(new BoundedBodyRequest(request), response);
        } catch (Exception exception) {
            ApiError error = exception instanceof ApiException apiException ? apiException.error()
                    : exception instanceof DataAccessResourceFailureException
                    ? ApiError.DEPENDENCY_UNAVAILABLE : ApiError.INTERNAL_ERROR;
            problems.write(error, request, response, exception);
        } finally {
            MDC.remove("traceId");
        }
    }

    /** error dispatch도 서버가 생성한 식별자로 보호한다. */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
