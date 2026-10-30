package com.example.starter.common.web;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 지수 표현·이진 부동소수점 없이 정밀 수량을 처리한다. */
public final class DecimalValues {
    /** 정적 값 도구의 생성을 막는다. */
    private DecimalValues() {}

    /** 초과 소수 자릿수를 자동 절삭하지 않고 거부한다. */
    public static BigDecimal parse(String value, int maxScale) {
        if (!value.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")) {
            throw new ApiException(ApiError.MALFORMED_REQUEST);
        }

        BigDecimal decimal = new BigDecimal(value);

        if (decimal.scale() > maxScale) {
            throw new ApiException(ApiError.VALIDATION_FAILED);
        }

        return decimal;
    }

    /** 이번 정밀 계산 예제의 합계는 HALF_UP 두 자리로 반올림한다. */
    public static BigDecimal amount(BigDecimal unitPrice, BigDecimal quantity) {
        return unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
    }
}
