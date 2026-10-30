package com.example.starter.common.web;

import com.example.starter.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 필터와 MVC가 공유하는 오류 생성·직렬화·정제 진단 경계다. */
@Component
public final class ProblemResponses {
    private static final Logger LOG = LoggerFactory.getLogger(ProblemResponses.class);
    private final JsonMapper mapper;

    /** Boot의 JSON 설정으로 공개 오류를 직렬화한다. */
    public ProblemResponses(JsonMapper mapper) {
        this.mapper = mapper;
    }

    /** 허용한 필드만 새로 구성하며 헤더와 본문의 식별자를 맞춘다. */
    public ResponseEntity<Object> response(
            ErrorCode error,
            int status,
            HttpHeaders headers,
            List<ApiProblem.FieldError> fields,
            HttpServletRequest request,
            Throwable cause) {
        String traceId = RequestTrace.get(request);
        HttpHeaders safeHeaders = new HttpHeaders();
        // 프레임워크가 확정한 프로토콜 헤더만 보존하고 임의 예외 헤더는 폐기한다.
        for (String name : List.of("Allow", "Retry-After", "WWW-Authenticate", "Vary")) {
            if (headers.containsHeader(name)) {
                safeHeaders.put(name, headers.get(name));
            }
        }

        safeHeaders.set("X-Request-ID", traceId);
        safeHeaders.set("Cache-Control", "no-store");
        safeHeaders.set("Content-Type", "application/problem+json");
        String title = HttpStatus.valueOf(status).getReasonPhrase();
        ApiProblem problem =
                new ApiProblem("about:blank", title, status, error.name(), traceId, fields);
        diagnose(error, traceId, cause);

        return new ResponseEntity<>(problem, safeHeaders, status);
    }

    /** 응답 전송 뒤에는 두 번째 본문을 쓰지 않고 안전하게 종료한다. */
    public void write(
            ErrorCode error,
            HttpServletRequest request,
            HttpServletResponse response,
            Throwable cause)
            throws IOException {
        if (response.isCommitted()) {
            diagnose(error, RequestTrace.get(request), cause);

            return;
        }

        ResponseEntity<Object> entity =
                response(
                        error,
                        error.status().value(),
                        new HttpHeaders(),
                        List.of(),
                        request,
                        cause);
        response.resetBuffer();
        response.setStatus(entity.getStatusCode().value());
        if (cause instanceof ApiException failure && failure.retryAfterSeconds() > 0) {
            response.setHeader("Retry-After", Long.toString(failure.retryAfterSeconds()));
        }
        entity.getHeaders()
                .forEach(
                        (name, values) -> {
                            response.setHeader(name, values.getFirst());

                            for (int index = 1; index < values.size(); index++) {
                                response.addHeader(name, values.get(index));
                            }
                        });

        if (!"HEAD".equals(request.getMethod())) {
            mapper.writeValue(response.getOutputStream(), entity.getBody());
        }
    }

    /** 원문·cause 메시지·경로·SQL을 출력하지 않고 고정 분류와 코드 위치만 기록한다. */
    private void diagnose(ErrorCode error, String traceId, Throwable cause) {
        if (cause == null) {
            return;
        }

        if (error == ApiError.INTERNAL_ERROR || error.status().value() >= 500) {
            LOG.error("api_failure code={} traceId={}", error.name(), traceId, cause);
        } else {
            LOG.warn("api_failure code={} traceId={}", error.name(), traceId, cause);
        }
    }
}
