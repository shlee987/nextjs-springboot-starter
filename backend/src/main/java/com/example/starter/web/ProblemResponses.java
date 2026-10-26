package com.example.starter.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
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
    public ResponseEntity<Object> response(ApiError error, int status, HttpHeaders headers,
            List<ApiProblem.FieldError> fields, HttpServletRequest request, Throwable cause) {
        String traceId = RequestTrace.get(request);
        HttpHeaders safeHeaders = new HttpHeaders();
        // 프레임워크가 확정한 프로토콜 헤더만 보존하고 임의 예외 헤더는 폐기한다.
        for (String name : List.of("Allow", "Retry-After", "WWW-Authenticate")) {
            if (headers.containsHeader(name)) {
                safeHeaders.put(name, headers.get(name));
            }
        }

        safeHeaders.set("X-Request-ID", traceId);
        safeHeaders.set("Cache-Control", "no-store");
        safeHeaders.set("Content-Type", "application/problem+json");
        String title = org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase();
        ApiProblem problem = new ApiProblem("about:blank", title, status, error.name(),
                traceId, fields);
        diagnose(error, traceId, cause);

        return new ResponseEntity<>(problem, safeHeaders, status);
    }

    /** 응답 전송 뒤에는 두 번째 본문을 쓰지 않고 안전하게 종료한다. */
    public void write(ApiError error, HttpServletRequest request, HttpServletResponse response,
            Throwable cause) throws IOException {
        if (response.isCommitted()) {
            diagnose(error, RequestTrace.get(request), cause);

            return;
        }

        ResponseEntity<Object> entity = response(error, error.status().value(), new HttpHeaders(),
                List.of(), request, cause);
        response.resetBuffer();
        response.setStatus(entity.getStatusCode().value());
        entity.getHeaders().forEach((name, values) -> response.setHeader(name, values.getFirst()));

        if (!"HEAD".equals(request.getMethod())) {
            mapper.writeValue(response.getOutputStream(), entity.getBody());
        }
    }

    /** 원문·cause 메시지·경로·SQL을 출력하지 않고 고정 분류와 코드 위치만 기록한다. */
    private void diagnose(ApiError error, String traceId, Throwable cause) {
        if (cause == null) {
            return;
        }

        StringBuilder diagnostic = new StringBuilder();
        Throwable current = cause;

        // 원인별 클래스와 제한된 코드 위치만 남긴다. 메시지·파일 경로·suppressed 내용은 읽지 않는다.
        for (int depth = 0; current != null && depth < 4; depth++) {
            diagnostic.append(current.getClass().getName()).append('[');
            StackTraceElement[] stack = current.getStackTrace();

            for (int index = 0; index < Math.min(stack.length, 8); index++) {
                StackTraceElement frame = stack[index];
                diagnostic.append(frame.getClassName()).append('.').append(frame.getMethodName())
                        .append(':').append(frame.getLineNumber()).append(';');
            }

            diagnostic.append(']');
            current = current.getCause();
        }

        LOG.warn("api_failure code={} traceId={} diagnostic={}", error.name(), traceId, diagnostic);
    }
}
