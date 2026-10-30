package com.example.starter.common.web;

import java.io.IOException;
import java.util.List;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** ProblemDetail을 허용 필드로 제한하고 HEAD의 열린 파일 핸들을 해제한 뒤 본문을 제거한다. */
@RestControllerAdvice
public final class ProblemDetailBoundary implements ResponseBodyAdvice<Object> {
    private final ProblemResponses problems;

    /** 공통 오류 생성 경계를 연결한다. */
    public ProblemDetailBoundary(ProblemResponses problems) {
        this.problems = problems;
    }

    /** 반환 타입이 ResponseEntity일 때도 실제 ProblemDetail을 검사한다. */
    @Override
    public boolean supports(
            MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    /** HEAD 자원을 닫고 본문을 제거하며 나머지 응답의 오류 진단 필드만 정제한다. */
    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (HttpMethod.HEAD.equals(request.getMethod())) {
            // 본문을 제거하면 Resource converter의 finally가 실행되지 않으므로 열린 핸들을 해제한다.
            if (body instanceof InputStreamResource resource) {
                try (var stream = resource.getInputStream()) {
                    // HEAD는 바이트를 읽지 않고 GET과 같은 권한·감사·헤더만 유지한다.
                } catch (IOException failure) {
                    throw new ApiException(ApiError.DEPENDENCY_UNAVAILABLE, failure);
                }
            }

            return null;
        }

        if (body instanceof ProblemDetail detail
                && request instanceof ServletServerHttpRequest servletRequest) {
            int status =
                    detail.getStatus() >= 400 && detail.getStatus() <= 599
                            ? detail.getStatus()
                            : 500;
            var entity =
                    problems.response(
                            ApiError.fromStatus(status),
                            status,
                            new HttpHeaders(),
                            List.of(),
                            servletRequest.getServletRequest(),
                            null);
            response.setStatusCode(entity.getStatusCode());
            response.getHeaders().putAll(entity.getHeaders());

            return entity.getBody();
        }

        return body;
    }
}
