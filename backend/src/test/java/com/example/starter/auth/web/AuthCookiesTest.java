package com.example.starter.auth.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.auth.model.IssuedTokens;
import com.example.starter.common.web.ApiError;
import com.example.starter.common.web.ProblemResponses;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** 원문 JWT 생성 없이 쿠키 수명·보안 속성과 오류 경계 보존을 확인한다. */
class AuthCookiesTest {
    /** access10m와 별개로 cookie는 절대8h, 401은 최신 자격을 삭제하지 않는다. */
    @Test
    void accessCookieLivesUntilAbsoluteExpiryAndSurvives401() throws Exception {
        Instant now = Instant.parse("2026-10-29T00:00:00Z");

        for (String prefix : new String[] {"local-", "__Host-"}) {
            var environment =
                    new MockEnvironment()
                            .withProperty("app.cookies.prefix", prefix)
                            .withProperty(
                                    "app.cookies.secure",
                                    Boolean.toString(prefix.equals("__Host-")));
            var cookies = new AuthCookies(environment, Clock.fixed(now, ZoneOffset.UTC));
            var response = new MockHttpServletResponse();
            cookies.issue(
                    response,
                    new IssuedTokens(
                            "SYNTHETIC_ACCESS_MARKER",
                            "SYNTHETIC_REFRESH_MARKER",
                            now.plusSeconds(600),
                            now.plusSeconds(28800),
                            1,
                            "synthetic-sid",
                            "[]"));
            var values = response.getHeaders("Set-Cookie");
            assertEquals(2, values.size());
            assertTrue(values.stream().anyMatch(value -> value.startsWith(prefix + "access=")));
            assertTrue(
                    values.stream()
                            .allMatch(
                                    value ->
                                            value.contains("Max-Age=28800")
                                                    && value.contains("HttpOnly")
                                                    && value.contains("SameSite=Lax")
                                                    && value.contains("Path=/")
                                                    && !value.contains("Domain=")));
            values.forEach(
                    value -> assertEquals(prefix.equals("__Host-"), value.contains("Secure")));
            new ProblemResponses(JsonMapper.builder().build())
                    .write(ApiError.UNAUTHENTICATED, new MockHttpServletRequest(), response, null);
            assertTrue(
                    response.getHeaders("Set-Cookie").stream()
                            .allMatch(value -> !value.contains("Max-Age=0")));
            assertFalse(response.getContentAsString().contains("SYNTHETIC_ACCESS_MARKER"));
            assertFalse(response.getContentAsString().contains("SYNTHETIC_REFRESH_MARKER"));
        }
    }
}
