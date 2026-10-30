package com.example.starter.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.TestPolicies;
import com.example.starter.auth.web.AuthRequests;
import com.example.starter.common.config.AppProperties;
import com.example.starter.common.config.JsonConfiguration;
import com.example.starter.common.message.ServerMessages;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.support.ResourceBundleMessageSource;
import tools.jackson.databind.json.JsonMapper;

/** BF02/BF03의 날짜·정밀도·입력·메시지를 실제 제품 parser로 검증한다. */
class FoundationValuesTest {
    /** 윤년·불가능 날짜·offset·네 자리 양수 연도를 엄격하게 구분한다. */
    @Test
    void strictUtcFormatsAndTruncation() {
        assertEquals(LocalDate.of(2024, 2, 29), UtcTimes.parseDate("2024-02-29"));
        assertThrows(RuntimeException.class, () -> UtcTimes.parseDate("2023-02-29"));
        assertThrows(RuntimeException.class, () -> UtcTimes.parseDate("0000-01-01"));
        assertThrows(RuntimeException.class, () -> UtcTimes.parseDate("+10000-01-01"));
        Instant precise = Instant.parse("2026-10-29T14:30:00.123999Z");
        assertEquals("2026-10-29 14:30:00.123", UtcTimes.formatMillis(precise));
        assertEquals(Instant.parse("2026-10-29T14:30:00.123Z"), UtcTimes.millis(precise));
        assertEquals("2026-10-29 14:30:00", UtcTimes.formatSeconds(precise));
        assertEquals(
                Instant.parse("2026-10-29T14:30:00Z"),
                UtcTimes.parseSeconds("2026-10-29 14:30:00"));
        var json = JsonMapper.builder().addModule(JsonConfiguration.apiValueModule()).build();
        assertEquals("\"2026-10-29 14:30:00\"", json.writeValueAsString(new UtcSecond(precise)));
        assertEquals(
                new UtcSecond(Instant.parse("2026-10-29T14:30:00Z")),
                json.readValue("\"2026-10-29 14:30:00\"", UtcSecond.class));

        for (String invalid :
                List.of(
                        "2026-10-29T14:30:00.123Z",
                        "2026-10-29 14:30:00.123+09:00",
                        "2026-02-30 00:00:00.000",
                        "2026-10-29 24:00:00.000",
                        "2026-10-29 14:30:00.1234")) {
            assertEquals(
                    ApiError.VALIDATION_FAILED,
                    assertThrows(ApiException.class, () -> UtcTimes.parseMillis(invalid)).error());
        }
    }

    /** ID/decimal 문자열과 안전 건수 number·null·빈 배열을 구분한다. */
    @Test
    void jsonPreservesPrecisionAndPublicFormats() {
        JsonMapper mapper =
                JsonMapper.builder().addModule(JsonConfiguration.apiValueModule()).build();
        var value =
                new Values(
                        "9223372036854775807",
                        20L,
                        new BigDecimal("1000000000000000000.20"),
                        Instant.parse("2026-10-29T14:30:00.123999Z"),
                        LocalDate.of(2024, 2, 29),
                        null,
                        List.of());
        var tree = mapper.readTree(mapper.writeValueAsString(value));
        assertTrue(tree.get("id").isString());
        assertTrue(tree.get("count").isNumber());
        assertEquals("1000000000000000000.20", tree.get("amount").asString());
        assertEquals("2026-10-29 14:30:00.123", tree.get("time").asString());
        assertTrue(tree.get("optional").isNull());
        assertEquals(0, tree.get("items").size());
        var restored = mapper.readValue(mapper.writeValueAsString(value), Values.class);
        assertEquals(UtcTimes.millis(value.time()), restored.time());
        assertThrows(RuntimeException.class, () -> mapper.readValue("1.2", BigDecimal.class));
        assertThrows(RuntimeException.class, () -> mapper.readValue("\"1E+3\"", BigDecimal.class));
        assertThrows(
                ApiException.class, () -> PageData.of(List.of(), 0, 20, 9_007_199_254_740_992L));
        assertEquals(Long.MAX_VALUE, ApiIds.parse("9223372036854775807"));
        assertThrows(ApiException.class, () -> ApiIds.parse("9223372036854775808"));
    }

    /** HALF_UP 2자리 계산과 초과 소수 거부를 확인한다. */
    @Test
    void decimalBusinessExample() {
        assertEquals(
                new BigDecimal("0.30"),
                DecimalValues.amount(new BigDecimal("0.1"), new BigDecimal("3")));
        assertEquals(
                new BigDecimal("-1.01"),
                DecimalValues.amount(new BigDecimal("-1.005"), BigDecimal.ONE));
        assertThrows(ApiException.class, () -> DecimalValues.parse("1.234", 2));
    }

    /** 로그인 최소 길이는1, 신규는15 code point이며 UTF-16 surrogate를 중복 세지 않는다. */
    @Test
    void passwordsUseCodePointPolicyWithoutNormalization() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(new AuthRequests.Login("localuser", "x")).isEmpty());
            assertTrue(
                    validator
                            .validate(new AuthRequests.Password("old", "😀".repeat(128)))
                            .isEmpty());
            assertFalse(
                    validator
                            .validate(new AuthRequests.Password("old", "😀".repeat(129)))
                            .isEmpty());
            assertFalse(validator.validate(new AuthRequests.Login(" Localuser", "x")).isEmpty());
            assertFalse(
                    validator.validate(new AuthRequests.Password("old", "x".repeat(14))).isEmpty());
            assertFalse(
                    validator
                            .validate(new AuthRequests.Password("old", "x".repeat(14) + "\ud800"))
                            .isEmpty());
        }
    }

    /** typed 설정의 틀린 enum·음수 수명·skew·상한을 검증한다. */
    @Test
    void typedPoliciesRejectInvalidValues() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(TestPolicies.local()).isEmpty());

            for (var override :
                    List.of(
                            new String[] {"app.auth.idle-timeout", "-1s"},
                            new String[] {"app.auth.jwt.clock-skew", "61s"},
                            new String[] {"app.audit.query.max-range-days", "32"},
                            new String[] {"app.server-id", "../unsafe"})) {
                var environment = TestPolicies.environment().withProperty(override[0], override[1]);
                AppProperties properties =
                        Binder.get(environment).bind("app", AppProperties.class).get();
                assertFalse(validator.validate(properties).isEmpty(), override[0]);
            }

            var invalid = TestPolicies.environment().withProperty("app.auth.state-store", "memory");
            assertThrows(
                    RuntimeException.class,
                    () -> Binder.get(invalid).bind("app", AppProperties.class));
        }
    }

    /** 모든 공개 코드에 한국어 자원이 있고 누락 키는 고정 일반 문구다. */
    @Test
    void messagesHaveSafeFallback() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("messages/errors");
        source.setDefaultEncoding("UTF-8");
        var messages = new ServerMessages(source);

        for (ApiError code : ApiError.values()) {
            assertFalse(messages.resolve(code.messageKey()).equals(code.messageKey()));
        }

        assertEquals("요청을 처리하지 못했습니다.", messages.resolve("SYNTHETIC_MISSING_SECRET_KEY"));
    }

    /** roundtrip용 비밀 없는 계약 모델이다. */
    record Values(
            String id,
            long count,
            BigDecimal amount,
            Instant time,
            LocalDate date,
            String optional,
            List<String> items) {}
}
