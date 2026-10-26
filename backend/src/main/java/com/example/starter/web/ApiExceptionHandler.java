package com.example.starter.web;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** MVC 오류와 업무 실패를 원문 정보가 없는 공통 응답으로 변환한다. */
@RestControllerAdvice
public final class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private final ProblemResponses problems;

    /** 필터와 동일한 오류 출력 경계를 사용한다. */
    public ApiExceptionHandler(ProblemResponses problems) {
        this.problems = problems;
    }

    /** 프레임워크의 상태·Allow 헤더를 보존하고 ProblemDetail 본문은 폐기한다. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ApiError error = exception instanceof MethodArgumentNotValidException
                || exception instanceof HandlerMethodValidationException
                ? ApiError.VALIDATION_FAILED : ApiError.fromStatus(status.value());

        if (exception.getCause() instanceof ApiException apiException) {
            error = apiException.error();
            status = error.status();
        }

        List<ApiProblem.FieldError> fields = List.of();

        if (exception instanceof MethodArgumentNotValidException invalid) {
            // 요청 DTO가 실제로 선언한 직접 필드만 공개한다. 내부·중첩 경로는 기능 계약에서 확장한다.
            Set<String> allowed = Arrays.stream(
                            invalid.getParameter().getParameterType().getDeclaredFields())
                    .filter(field -> !Modifier.isStatic(field.getModifiers())
                            && !field.isSynthetic())
                    .map(java.lang.reflect.Field::getName).collect(Collectors.toSet());
            fields = invalid.getBindingResult().getFieldErrors().stream()
                    .filter(field -> allowed.contains(field.getField()))
                    .map(field -> new ApiProblem.FieldError(field.getField(),
                            validationCode(field.getCode())))
                    .distinct().limit(100).toList();
        }

        return problems.response(error, status.value(), headers, fields,
                ((ServletWebRequest) request).getRequest(), exception);
    }

    /** 등록된 업무 실패와 미분류 실패를 구분하며 원인을 응답에 복사하지 않는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleFailure(Exception exception, ServletWebRequest request) {
        ApiError error = exception instanceof ApiException apiException
                ? apiException.error() : ApiError.INTERNAL_ERROR;
        int status = error.status().value();
        HttpHeaders headers = new HttpHeaders();

        if (exception instanceof ErrorResponse frameworkError) {
            status = frameworkError.getStatusCode().value();
            headers = frameworkError.getHeaders();
            error = ApiError.fromStatus(status);
        }

        return problems.response(error, status, headers, List.of(),
                request.getRequest(), exception);
    }

    /** 프레임워크 제약 이름을 공개 검증 코드 네 개로 한정한다. */
    private static String validationCode(String constraint) {
        if (constraint == null) {
            return "INVALID_FORMAT";
        }

        return switch (constraint) {
            case "NotNull", "NotBlank", "NotEmpty" -> "REQUIRED";
            case "Size", "Length" -> "INVALID_LENGTH";
            case "Min", "Max", "DecimalMin", "DecimalMax", "Positive", "PositiveOrZero",
                    "Negative", "NegativeOrZero" -> "OUT_OF_RANGE";
            default -> "INVALID_FORMAT";
        };
    }
}
