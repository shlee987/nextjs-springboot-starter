package com.example.starter.common.web;

import com.example.starter.audit.service.DenialAuditService;
import com.example.starter.auth.model.AuthError;
import com.example.starter.auth.store.AuthStateStore;
import com.example.starter.auth.web.RequestRateFilter;
import com.example.starter.common.error.ErrorCode;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/** Session·Security를 포함한 후속 필터의 실패를 같은 공개 계약으로 처리한다. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class RequestBoundaryFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestBoundaryFilter.class);
    private static final String LOGGED = RequestBoundaryFilter.class.getName() + ".logged";
    private final ProblemResponses problems;
    private final String serverId;
    private final boolean documentation;
    private final DenialAuditService audits;
    private final AuthStateStore states;
    private final Clock clock;

    /** Servlet 체인의 최외곽에 공통 오류 출력을 연결한다. */
    public RequestBoundaryFilter(
            ProblemResponses problems,
            Environment environment,
            DenialAuditService audits,
            AuthStateStore states,
            Clock clock) {
        this.problems = problems;
        this.serverId = environment.getRequiredProperty("app.server-id");
        this.documentation =
                environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class, false);
        this.audits = audits;
        this.states = states;
        this.clock = clock;
    }

    /** 요청 MDC를 정리하며 조기 거부부터 공통 헤더와 안전한 오류 JSON을 적용한다. */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = RequestTrace.get(request);
        response.setHeader("X-Request-ID", traceId);
        MDC.clear();
        MDC.put("traceId", traceId);
        var context = new RequestContext(traceId, RequestContext.peer(request), serverId);
        request.setAttribute(RequestContext.ATTRIBUTE, context);
        RequestContext.bind(context);
        long started = System.nanoTime();
        AtomicBoolean logged = (AtomicBoolean) request.getAttribute(LOGGED);

        if (logged == null) {
            logged = new AtomicBoolean();
            request.setAttribute(LOGGED, logged);
        }

        AtomicBoolean completion = logged;

        try {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader(
                    "Content-Security-Policy",
                    "default-src 'none'; base-uri 'none'; "
                            + "frame-ancestors 'none'; form-action 'none'");

            if (documentation
                    && "GET".equals(request.getMethod())
                    && (request.getServletPath().startsWith("/swagger-ui/")
                            || request.getServletPath().equals("/swagger-ui.html"))) {
                response.setHeader(
                        "Content-Security-Policy",
                        "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline';"
                            + " connect-src 'self'; img-src 'self' data:; font-src 'self'; base-uri"
                            + " 'none'; frame-ancestors 'none'; form-action 'self'");
            }

            if (request.getContentLengthLong() > RequestBodyPolicy.limit(request)) {
                throw new ApiException(ApiError.PAYLOAD_TOO_LARGE);
            }

            chain.doFilter(new BoundedBodyRequest(request), response);
        } catch (Exception exception) {
            ErrorCode error =
                    exception instanceof ApiException apiException
                            ? apiException.error()
                            : exception instanceof DataAccessResourceFailureException
                                    ? ApiError.DEPENDENCY_UNAVAILABLE
                                    : ApiError.INTERNAL_ERROR;
            Throwable diagnosticCause = exception;

            try {
                if (error == AuthError.TOKEN_REVOKED) {
                    RequestRateFilter.limitAnonymous(states, clock, request);
                }

                if (error == AuthError.ORIGIN_REJECTED) {
                    audits.denied(request, error.name());
                } else if (error == AuthError.AUTH_STATE_UNAVAILABLE
                        || error == AuthError.TOKEN_REVOKED
                        || error == ApiError.RATE_LIMITED) {
                    audits.failed(error);
                }
            } catch (ApiException auditFailure) {
                error = auditFailure.error();
                diagnosticCause = auditFailure;

                if (error == ApiError.RATE_LIMITED) {
                    try {
                        audits.failed(error);
                    } catch (ApiException unavailableAudit) {
                        error = unavailableAudit.error();
                        diagnosticCause = unavailableAudit;
                    }
                }
            }

            problems.write(error, request, response, diagnosticCause);
        } finally {
            RequestContext.clear();
            if (request.isAsyncStarted()) {
                request.getAsyncContext()
                        .addListener(
                                new AsyncListener() {
                                    /** 비동기 응답이 실제 완료되었을 때 한 번만 종료를 기록한다. */
                                    @Override
                                    public void onComplete(AsyncEvent event) {
                                        logCompleted(request, response, started, completion);
                                    }

                                    /** 종료 이벤트가 최종 상태를 기록하므로 여기서 중복 기록하지 않는다. */
                                    @Override
                                    public void onTimeout(AsyncEvent event) {}

                                    /** 안전 오류 경계가 담당한 원인을 다시 출력하지 않는다. */
                                    @Override
                                    public void onError(AsyncEvent event) {}

                                    /** 다음 async cycle에도 같은 단회 완료 guard를 유지한다. */
                                    @Override
                                    public void onStartAsync(AsyncEvent event) {
                                        event.getAsyncContext().addListener(this);
                                    }
                                });
            } else {
                logCompleted(request, response, started, completion);
            }

            MDC.clear();
        }
    }

    /** error dispatch도 서버가 생성한 식별자로 보호한다. */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    /** raw URI·query·쿠키 없이 서버 route template과 최종 상태만 기록한다. */
    private void logCompleted(
            HttpServletRequest request,
            HttpServletResponse response,
            long started,
            AtomicBoolean logged) {
        String path = request.getRequestURI();

        if (!path.startsWith("/api/") && !path.startsWith("/auth/") && !path.equals("/error")) {
            return;
        }
        if (!logged.compareAndSet(false, true)) {
            return;
        }

        Object matched = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = matched == null ? "UNMATCHED" : matched.toString();
        Object actor = request.getAttribute(RequestContext.ACTOR_ATTRIBUTE);
        MDC.put("traceId", RequestTrace.get(request));

        if (actor instanceof Long id) {
            MDC.put("actorUserId", id.toString());
        }

        try {
            LOG.info(
                    "request_completed method={} routeTemplate={} status={} elapsedMs={} traceId={}"
                            + " actorId={}",
                    request.getMethod().matches("GET|HEAD|POST|PUT|PATCH|DELETE|OPTIONS")
                            ? request.getMethod()
                            : "OTHER",
                    route,
                    response.getStatus(),
                    (System.nanoTime() - started) / 1_000_000,
                    RequestTrace.get(request),
                    actor);
        } finally {
            MDC.clear();
        }
    }
}
