package com.example.starter.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.starter.TestPolicies;
import com.example.starter.TestTransactions;
import com.example.starter.audit.mapper.AuditMapper;
import com.example.starter.audit.model.AuditEvent;
import com.example.starter.audit.service.AuditWriter;
import com.example.starter.auth.config.PasswordConfiguration;
import com.example.starter.auth.model.AuthError;
import com.example.starter.auth.store.AuthStateStore;
import com.example.starter.auth.web.AuthRequests;
import com.example.starter.common.web.ApiException;
import com.example.starter.user.mapper.UserMapper;
import com.example.starter.user.model.UserAccount;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

/** 실제 Argon2와 로그인 실패 업무를 검증하며 DB·JWT/개발 키·상태 자격은 생성하지 않는다. */
class LoginFailureContractTest {
    /** 미존재/비활성/외부계정/손상 hash/오류 password 모두 같은401과 실패 이력이다. */
    @Test
    void credentialFailuresAreUniformAndNeverCreateLoginState() {
        String seed =
                "{argon2}$argon2id$v=19$m=19456,t=2,p=1$xEIADau0nITStXj1om5P2g$VtAhVuavHavD6NUrNs/l/2qmT0CK8TQuVYmO4eXzeOk";
        Instant now = Instant.parse("2026-10-29T00:00:00Z");
        var cases =
                List.of(
                        new FailureCase(null, "x"),
                        new FailureCase(user("INACTIVE", seed, now), "LocalExample1234!"),
                        new FailureCase(user("ACTIVE", null, now), "LocalExample1234!"),
                        new FailureCase(
                                user("ACTIVE", "unapproved-hash", now), "LocalExample1234!"),
                        new FailureCase(user("ACTIVE", "{argon2}malformed", now), "x"),
                        new FailureCase(user("ACTIVE", seed, now), "wrong-password"));
        var selected = new AtomicReference<UserAccount>();
        var failures = new AtomicInteger();
        var audits = new AtomicInteger();
        var manager = new TestTransactions();
        UserMapper users =
                (UserMapper)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {UserMapper.class},
                                (proxy, method, arguments) -> {
                                    if (method.getName().equals("selectAccountByLoginId")) {
                                        return selected.get();
                                    }

                                    throw new AssertionError(
                                            "No login DB mutation is allowed in failure tests");
                                });
        AuthStateStore states =
                (AuthStateStore)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {AuthStateStore.class},
                                (proxy, method, arguments) -> {
                                    if (method.getName().equals("consume")
                                            || method.getName().equals("accountRetry")) {
                                        return 0L;
                                    }

                                    if (method.getName().equals("failed")) {
                                        failures.incrementAndGet();

                                        return null;
                                    }

                                    throw new AssertionError("No login credential may be created");
                                });
        AuditMapper mapper =
                (AuditMapper)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {AuditMapper.class},
                                (proxy, method, arguments) -> {
                                    assertEquals(
                                            "LOGIN_FAILED",
                                            ((AuditEvent) arguments[0]).eventCode());
                                    audits.incrementAndGet();

                                    return 1;
                                });

        try (var context = new AnnotationConfigApplicationContext(PasswordConfiguration.class)) {
            var clock = Clock.fixed(now, ZoneOffset.UTC);
            var writer =
                    new AuditWriter(
                            mapper,
                            clock,
                            TestPolicies.local(),
                            manager,
                            TestPolicies.references());
            var service =
                    new AuthLoginService(
                            users,
                            states,
                            writer,
                            context.getBean(PasswordEncoder.class),
                            null,
                            TestPolicies.local(),
                            null,
                            null,
                            null,
                            clock,
                            JsonMapper.builder().build(),
                            manager);

            for (var input : cases) {
                selected.set(input.user());
                assertEquals(
                        AuthError.LOGIN_FAILED,
                        assertThrows(
                                        ApiException.class,
                                        () ->
                                                service.login(
                                                        new AuthRequests.Login(
                                                                "localuser", input.password()),
                                                        "127.0.0.1"))
                                .error());
            }
        }

        assertEquals(cases.size(), failures.get());
        assertEquals(cases.size(), audits.get());
    }

    /** 비밀 없는 합성 사용자 조회 결과다. */
    private UserAccount user(String status, String hash, Instant now) {
        return new UserAccount(1, "localuser", "합성 사용자", null, status, now, now, 0, hash);
    }

    /** 검증 실패 입력은 기본 toString으로 비밀번호를 출력하지 않는다. */
    private record FailureCase(UserAccount user, String password) {
        /** 진단 출력에서 합성 비밀번호도 숨긴다. */
        @Override
        public String toString() {
            return "FailureCase[redacted]";
        }
    }
}
