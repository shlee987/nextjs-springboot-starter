package com.example.starter.common.web;

/** 공개 bigint ID는 문자열로 검증하고 내부 long으로만 변환한다. */
public final class ApiIds {
    /** ID 도구의 인스턴스 생성을 막는다. */
    private ApiIds() {}

    /** canonical 양수 숫자 문자열만 허용한다. */
    public static long parse(String value) {
        try {
            if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
                throw new IllegalArgumentException();
            }

            return Long.parseLong(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ApiError.VALIDATION_FAILED);
        }
    }
}
