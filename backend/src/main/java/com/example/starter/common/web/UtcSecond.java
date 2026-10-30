package com.example.starter.common.web;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.Instant;

/** 초 단위로 명시한 API에서만 사용하는 시점 값이다. */
public record UtcSecond(Instant instant) {
    /** 엄격한 초 단위 UTC 문자열을 해석한다. */
    @JsonCreator
    public static UtcSecond parse(String value) {
        return new UtcSecond(UtcTimes.parseSeconds(value));
    }

    /** 프로토콜 NumericDate 대신 일반 API 초 문자열을 반환한다. */
    @JsonValue
    public String value() {
        return UtcTimes.formatSeconds(instant);
    }
}
