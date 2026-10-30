package com.example.starter.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 승인 후만 실행하는 실제 HTTP 쓰기 시험이다. 서버·컨테이너·DB를 자동 생성하지 않는다. */
@Tag("live")
class LiveAuthContractTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static String serverA;
    private static String serverB;
    private static String origin;

    /** 실행 대상 loopback A/B와 승인 표식을 요구한다. 원문 token은 출력하지 않는다. */
    @BeforeAll
    static void approvedTargets() {
        assertEquals(
                "approved",
                System.getenv("STARTER_LIVE_AUTH_TESTS"),
                "Live tests require explicit execution approval");
        serverA = System.getenv().getOrDefault("STARTER_TEST_SERVER_A", "http://127.0.0.1:8080");
        serverB = System.getenv().getOrDefault("STARTER_TEST_SERVER_B", "http://127.0.0.1:8081");
        origin = System.getenv().getOrDefault("APP_PUBLIC_ORIGIN", "http://localhost:3000");

        for (String server : new String[] {serverA, serverB}) {
            URI uri = URI.create(server);
            assertTrue(Set.of("127.0.0.1", "localhost", "[::1]").contains(uri.getHost()));
            assertEquals("http", uri.getScheme());
            assertEquals("", uri.getPath());
        }
    }

    /** A 발급→B 조회와 A logout→B 거부를 실제 쿠키로 검증한다. */
    @Test
    void crossServerLoginAndLogout() throws Exception {
        var browser = new Browser();

        try {
            browser.login();
            assertEquals(200, browser.send(serverB, "GET", "/api/auth/me", null).statusCode());
            assertEquals(200, browser.send(serverB, "HEAD", "/api/auth/me", null).statusCode());
            assertEquals(204, browser.send(serverA, "POST", "/api/auth/logout", null).statusCode());
            assertEquals(401, browser.send(serverB, "GET", "/api/auth/me", null).statusCode());
        } finally {
            browser.send(serverA, "POST", "/api/auth/logout", null);
        }
    }

    /** 과거 refresh 재사용이 현재 sid를 폐기하고 원문을 응답 본문에 내보내지 않는다. */
    @Test
    void refreshRotationAndReplay() throws Exception {
        var browser = new Browser();

        try {
            browser.login();
            String old = browser.cookies.get("local-refresh");
            var refreshed = browser.send(serverB, "POST", "/api/auth/refresh", null);
            assertEquals(200, refreshed.statusCode());
            assertFalse(old.equals(browser.cookies.get("local-refresh")));
            assertFalse(refreshed.body().contains(old));
            browser.cookies.put("local-refresh", old);
            var replay = browser.send(serverA, "POST", "/api/auth/refresh", null);
            assertEquals(401, replay.statusCode());
            assertEquals(
                    "TOKEN_REFRESH_REUSED", JSON.readTree(replay.body()).get("code").asString());
            assertEquals(401, browser.send(serverB, "GET", "/api/auth/me", null).statusCode());
        } finally {
            browser.send(serverA, "POST", "/api/auth/logout", null);
        }
    }

    /** 같은 refresh의 병렬 회전은 최대 한 번만 발급하고 재사용 후 두 요청 모두 무효화된다. */
    @Test
    void concurrentRefreshIsSingleUse() throws Exception {
        var browser = new Browser();

        try {
            browser.login();
            var other = browser.copy();
            var first = CompletableFuture.supplyAsync(() -> browser.uncheckedRefresh(serverA));
            var second = CompletableFuture.supplyAsync(() -> other.uncheckedRefresh(serverB));
            var attempts = List.of(first.join(), second.join());
            long success = attempts.stream().filter(attempt -> attempt.status() == 200).count();
            assertTrue(success <= 1);
            assertTrue(
                    attempts.stream()
                            .allMatch(attempt -> Set.of(200, 401).contains(attempt.status())));
            assertEquals(
                    1,
                    attempts.stream()
                            .filter(attempt -> "TOKEN_REFRESH_REUSED".equals(attempt.code()))
                            .count());
            // CAS 승자도 후속 발급 검증 전에 재사용 폐기를 관찰하면 TOKEN_REVOKED401이다.
            assertTrue(
                    attempts.stream()
                            .filter(attempt -> attempt.status() == 401)
                            .allMatch(
                                    attempt ->
                                            Set.of("TOKEN_REFRESH_REUSED", "TOKEN_REVOKED")
                                                    .contains(attempt.code())));
            assertEquals(401, browser.send(serverA, "GET", "/api/auth/me", null).statusCode());
        } finally {
            browser.send(serverA, "POST", "/api/auth/logout", null);
        }
    }

    /** 미존재 합성 계정도 다섯 번 실패 후 429이며 IP 한도는 독립적으로 유지한다. */
    @Test
    void unknownAccountHasBoundedFailureWindow() throws Exception {
        var browser = new Browser();
        browser.csrf();
        String login = "missing-" + UUID.randomUUID().toString();
        String body = JSON.writeValueAsString(Map.of("loginId", login, "password", "x"));

        for (int index = 0; index < 5; index++) {
            assertEquals(401, browser.send(serverA, "POST", "/api/auth/login", body).statusCode());
        }

        var limited = browser.send(serverB, "POST", "/api/auth/login", body);
        assertEquals(429, limited.statusCode());
        assertTrue(Long.parseLong(limited.headers().firstValue("Retry-After").orElseThrow()) > 0);
    }

    /** 병렬 결과는 원문 응답 대신 공개 상태/코드만 보관한다. */
    private record RefreshAttempt(int status, String code) {}

    /** 테스트 client만 원문 쿠키를 보관하며 toString이나 로그로 출력하지 않는다. */
    private static class Browser {
        final Map<String, String> cookies = new LinkedHashMap<>();
        final HttpClient http = HttpClient.newHttpClient();
        String csrf;

        /** 공개 CSRF endpoint에서 XOR 표현과 cookie를 받는다. */
        void csrf() throws Exception {
            var response = send(serverA, "GET", "/api/auth/csrf", null);
            assertEquals(200, response.statusCode());
            csrf = JSON.readTree(response.body()).at("/data/token").asString();
        }

        /** 공개 합성 사용자로 로그인하고 교체된 CSRF를 다시 조회한다. */
        void login() throws Exception {
            csrf();
            var response =
                    send(
                            serverA,
                            "POST",
                            "/api/auth/login",
                            JSON.writeValueAsString(
                                    Map.of(
                                            "loginId",
                                            "localuser",
                                            "password",
                                            "LocalExample1234!")));
            assertEquals(200, response.statusCode());
            assertTrue(
                    response.headers().allValues("Set-Cookie").stream()
                            .filter(value -> value.startsWith("local-access="))
                            .allMatch(
                                    value ->
                                            value.contains("HttpOnly")
                                                    && value.contains("SameSite=Lax")
                                                    && value.contains("Path=/")
                                                    && !value.contains("Domain=")));
            csrf();
        }

        /** 요청/응답 본문과 Set-Cookie를 진단에 출력하지 않는다. */
        HttpResponse<String> send(String server, String method, String path, String body)
                throws Exception {
            var builder =
                    HttpRequest.newBuilder(URI.create(server + path))
                            .timeout(Duration.ofSeconds(10))
                            .header("Accept", "application/json");

            if (!cookies.isEmpty()) {
                builder.header(
                        "Cookie",
                        cookies.entrySet().stream()
                                .map(entry -> entry.getKey() + "=" + entry.getValue())
                                .collect(Collectors.joining("; ")));
            }

            if (!Set.of("GET", "HEAD").contains(method)) {
                builder.header("Origin", origin).header("X-CSRF-TOKEN", csrf);
            }

            if (body != null) {
                builder.header("Content-Type", "application/json");
            }

            var response =
                    http.send(
                            builder.method(
                                            method,
                                            body == null
                                                    ? HttpRequest.BodyPublishers.noBody()
                                                    : HttpRequest.BodyPublishers.ofString(body))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());

            for (String value : response.headers().allValues("Set-Cookie")) {
                String[] pair = value.split(";", 2)[0].split("=", 2);
                cookies.put(pair[0], pair[1]);
            }

            return response;
        }

        /** 병렬 회전 입력은 같은 쿠키 snapshot의 별도 client로 고정한다. */
        Browser copy() {
            var result = new Browser();
            result.cookies.putAll(cookies);
            result.csrf = csrf;

            return result;
        }

        /** 병렬 future의 checked HTTP 실패를 시험 실패로 전달한다. */
        RefreshAttempt uncheckedRefresh(String server) {
            try {
                var response = send(server, "POST", "/api/auth/refresh", null);
                return new RefreshAttempt(
                        response.statusCode(),
                        response.statusCode() == 401
                                ? JSON.readTree(response.body()).get("code").asString()
                                : null);
            } catch (Exception exception) {
                throw new IllegalStateException("Live refresh request failed", exception);
            }
        }
    }
}
