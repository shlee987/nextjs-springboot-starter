package com.example.starter.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** ProblemDetail의 임의 확장 속성을 복사하지 않는 공개 필드 허용목록이다. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiProblem(
        String type,
        String title,
        int status,
        String code,
        String traceId,
        List<FieldError> fieldErrors) {
    /** 공개 DTO 경로와 허용한 검증 식별자만 담는다. */
    public record FieldError(String field, String code) {}
}
