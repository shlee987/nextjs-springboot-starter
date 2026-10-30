package com.example.starter.common.web;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** 표준 프로토콜 epoch와 별개인 API의 세 가지 UTC 문자열 계약이다. */
public final class UtcTimes {
    private static final DateTimeFormatter DATE = formatter("uuuu-MM-dd");
    private static final DateTimeFormatter SECOND = formatter("uuuu-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MILLI = formatter("uuuu-MM-dd HH:mm:ss.SSS");

    /** 정적 형식 도구의 생성을 막는다. */
    private UtcTimes() {}

    /** 없는 날짜와 유효 연도 범위 밖의 날짜를 거부한다. */
    public static LocalDate parseDate(String value) {
        LocalDate date = LocalDate.parse(value, DATE);
        validateYear(date.getYear());

        return date;
    }

    /** 밀리초 미만은 저장·출력 전에 절삭한다. */
    public static Instant millis(Instant value) {
        return value.truncatedTo(ChronoUnit.MILLIS);
    }

    /** 밀리초 UTC 문자열을 시점으로 해석한다. offset은 허용하지 않는다. */
    public static Instant parseMillis(String value) {
        try {
            return parse(value, MILLI);
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new ApiException(ApiError.VALIDATION_FAILED);
        }
    }

    /** 초 단위 계약에서만 사용하는 UTC 해석이다. */
    public static Instant parseSeconds(String value) {
        try {
            return parse(value, SECOND);
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new ApiException(ApiError.VALIDATION_FAILED);
        }
    }

    /** UTC 밀리초 API·로그 문자열을 생성한다. */
    public static String formatMillis(Instant value) {
        validateYear(value.atOffset(ZoneOffset.UTC).getYear());

        return MILLI.withZone(ZoneOffset.UTC).format(millis(value));
    }

    /** 초 단위 API 문자열을 생성한다. */
    public static String formatSeconds(Instant value) {
        validateYear(value.atOffset(ZoneOffset.UTC).getYear());

        return SECOND.withZone(ZoneOffset.UTC).format(value);
    }

    /** STRICT 형식으로 지역 시각을 읽고 UTC에 고정한다. */
    private static Instant parse(String value, DateTimeFormatter formatter) {
        LocalDateTime date = LocalDateTime.parse(value, formatter);
        validateYear(date.getYear());

        return date.toInstant(ZoneOffset.UTC);
    }

    /** 같은 엄격도와 locale을 사용한다. */
    private static DateTimeFormatter formatter(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT);
    }

    /** 공개 계약의 네 자리 양수 연도만 허용한다. */
    private static void validateYear(int year) {
        if (year < 1 || year > 9999) {
            throw new IllegalArgumentException("Unsupported UTC year");
        }
    }
}
