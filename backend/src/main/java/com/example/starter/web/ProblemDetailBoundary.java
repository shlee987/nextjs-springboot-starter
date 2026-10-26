package com.example.starter.web;

import java.util.List;
import org.springframework.core.MethodParameter;
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

/** 명시적으로 반환된 ProblemDetail도 허용 필드로 제한하고 HEAD 본문을 제거한다. */
@RestControllerAdvice
public final class ProblemDetailBoundary implements ResponseBodyAdvice<Object> {
    private final ProblemResponses problems;

    /** 공통 오류 생성 경계를 연결한다. */
    public ProblemDetailBoundary(ProblemResponses problems) {
        this.problems = problems;
    }

    /** 반환 타입이 ResponseEntity일 때도 실제 ProblemDetail을 검사한다. */
    @Override
    public boolean supports(MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    /** 정상 DTO는 그대로 두고 진단 확장 필드가 있는 오류만 새로 구성한다. */
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        if (HttpMethod.HEAD.equals(request.getMethod())) {
            return null;
        }

        if (body instanceof ProblemDetail detail
                && request instanceof ServletServerHttpRequest servletRequest) {
            int status = detail.getStatus() >= 400 && detail.getStatus() <= 599
                    ? detail.getStatus() : 500;
            var entity = problems.response(ApiError.fromStatus(status), status, new HttpHeaders(),
                    List.of(), servletRequest.getServletRequest(), null);
            response.setStatusCode(entity.getStatusCode());
            response.getHeaders().putAll(entity.getHeaders());

            return entity.getBody();
        }

        return body;
    }
}
