package com.example.starter.auth.web;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 비밀번호를 UTF-16 길이가 아닌 Unicode code point 수로 검증한다. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CodePointLengthValidator.class)
public @interface CodePointLength {
    /** 생성/로그인의 다른 최소 길이를 명시한다. */
    int min() default 1;

    /** 프로토콜 입력 상한이다. */
    int max() default 128;

    /** 외부 응답은 이 문구 대신 고정 field code를 사용한다. */
    String message() default "{password.length}";

    /** Bean Validation group 확장점이다. */
    Class<?>[] groups() default {};

    /** Bean Validation payload 계약이다. */
    Class<? extends Payload>[] payload() default {};
}
