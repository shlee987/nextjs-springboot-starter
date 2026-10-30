package com.example.starter.auth.web;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** 입력의 trim·정규화·절단 없이 code point 수만 확인한다. */
public class CodePointLengthValidator implements ConstraintValidator<CodePointLength, String> {
    private int min;
    private int max;

    /** 요청별 annotation의 길이 계약을 읽는다. */
    @Override
    public void initialize(CodePointLength constraint) {
        min = constraint.min();
        max = constraint.max();
    }

    /** 필수 여부는 NotBlank가 처리하고 여기서는 길이만 검사한다. */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        int length = value.codePointCount(0, value.length());

        for (int offset = 0; offset < value.length(); ) {
            int point = value.codePointAt(offset);

            if (point >= Character.MIN_SURROGATE && point <= Character.MAX_SURROGATE) {
                return false;
            }

            offset += Character.charCount(point);
        }

        return length >= min && length <= max;
    }
}
