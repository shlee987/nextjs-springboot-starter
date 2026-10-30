package com.example.starter.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.starter.TestPolicies;
import com.example.starter.TestTransactions;
import com.example.starter.audit.mapper.AuditMapper;
import com.example.starter.audit.service.AuditWriter;
import com.example.starter.auth.config.SigningKeys;
import com.example.starter.auth.model.AuthError;
import com.example.starter.auth.model.AuthLogin;
import com.example.starter.auth.model.AuthPermissions;
import com.example.starter.auth.store.AuthStateStore;
import com.example.starter.auth.web.AuthController;
import com.example.starter.auth.web.AuthCookies;
import com.example.starter.auth.web.AuthRequests;
import com.example.starter.common.web.ApiException;
import com.example.starter.user.mapper.UserMapper;
import com.example.starter.user.model.UserAccount;
import com.example.starter.user.service.UserService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import tools.jackson.databind.json.JsonMapper;

/** 응답 projection 실패 주입으로 새 sid·쿠키·deny-new 후속 요청의 보상 계약을 검사한다. */
class LoginProjectionContractTest {
    /** 생성·발급 후 DB projection 오류가 새 로그인 잔류/409를 만들지 않는다. */
    @Test
    void projectionFailureRevokesNewSidBeforeAnyCookieAndAllowsNextLogin() {
        var fixture = new Fixture();
        var controller = fixture.controller();
        var response = new MockHttpServletResponse();
        fixture.projectionFails.set(true);
        RuntimeException failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                controller.login(
                                        fixture.request(), new MockHttpServletRequest(), response));
        assertEquals("synthetic projection failure", failure.getMessage());
        assertNull(response.getHeader("Set-Cookie"));
        assertNull(fixture.active.get());
        fixture.projectionFails.set(false);
        var next = new MockHttpServletResponse();
        assertEquals(
                200,
                controller
                        .login(fixture.request(), new MockHttpServletRequest(), next)
                        .getStatusCode()
                        .value());
        assertNotNull(fixture.active.get());
        assertEquals(3, next.getHeaders("Set-Cookie").size());
    }

    /** 손상 snapshot은503이고 보상 실패는 원래 오류의 suppressed 원인으로만 보존한다. */
    @Test
    void parsingFailureIsCompensatedAndCleanupFailureDoesNotReplaceOriginalFailure() {
        var fixture = new Fixture();
        var failure =
                assertThrows(
                        ApiException.class,
                        () ->
                                fixture.auth.login(
                                        fixture.request(),
                                        "127.0.0.1",
                                        tokens ->
                                                AuthPermissions.parse(
                                                        fixture.json, "broken snapshot")));
        assertEquals(AuthError.AUTH_STATE_UNAVAILABLE, failure.error());
        assertNull(fixture.active.get());
        fixture.revokeFails.set(true);
        var original = new IllegalStateException("synthetic projection failure");
        assertSame(
                original,
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                fixture.auth.login(
                                        fixture.request(),
                                        "127.0.0.1",
                                        tokens -> {
                                            throw original;
                                        })));
        assertEquals(1, original.getSuppressed().length);
        assertNotNull(fixture.active.get());
        assertEquals(
                AuthError.CONCURRENT_LOGIN_DENIED,
                assertThrows(
                                ApiException.class,
                                () -> fixture.auth.login(fixture.request(), "127.0.0.1"))
                        .error());
    }

    /** 암호화 키 생성/DB 없이 실제 서비스와 controller를 연결한다. */
    private static class Fixture {
        final AtomicReference<AuthLogin> active = new AtomicReference<>();
        final AtomicBoolean projectionFails = new AtomicBoolean();
        final AtomicBoolean revokeFails = new AtomicBoolean();
        final JsonMapper json = JsonMapper.builder().build();
        final Clock clock = Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC);
        final UserMapper users;
        final AuthLoginService auth;

        /** 메모리 상태는 이 실패 주입 시험만의 deny-new 관찰용이며 제품 fallback이 아니다. */
        Fixture() {
            var user =
                    new UserAccount(
                            1,
                            "localuser",
                            "Synthetic",
                            null,
                            "ACTIVE",
                            clock.instant(),
                            clock.instant(),
                            0,
                            "{argon2}$argon2id$synthetic");
            users =
                    proxy(
                            UserMapper.class,
                            (method, args) ->
                                    switch (method) {
                                        case "selectAccountByLoginId", "selectAccount" -> user;
                                        case "selectUserForUpdate" -> null;
                                        case "selectPermissionList" -> List.of("USER_READ_SELF");
                                        case "selectDepartmentList" -> {
                                            if (projectionFails.get()) {
                                                throw new IllegalStateException(
                                                        "synthetic projection failure");
                                            }
                                            yield List.of();
                                        }
                                        case "selectRoleList" -> List.of();
                                        default -> throw new AssertionError(method);
                                    });
            var states =
                    proxy(
                            AuthStateStore.class,
                            (method, args) ->
                                    switch (method) {
                                        case "consume", "accountRetry" -> 0L;
                                        case "create" -> {
                                            if (active.get() != null) {
                                                throw new ApiException(
                                                        AuthError.CONCURRENT_LOGIN_DENIED);
                                            }
                                            active.set((AuthLogin) args[0]);
                                            yield false;
                                        }
                                        case "require" -> active.get();
                                        case "revoke" -> {
                                            if (revokeFails.get()) {
                                                throw new ApiException(
                                                        AuthError.AUTH_STATE_UNAVAILABLE);
                                            }
                                            if (active.get() != null
                                                    && active.get().sid().equals(args[0])) {
                                                active.set(null);
                                            }
                                            yield null;
                                        }
                                        case "clearAccount" -> null;
                                        default -> throw new AssertionError(method);
                                    });
            var manager = new TestTransactions();
            var writer =
                    new AuditWriter(
                            proxy(AuditMapper.class, (method, args) -> 1),
                            clock,
                            TestPolicies.local(),
                            manager,
                            TestPolicies.references());
            var passwords =
                    new PasswordEncoder() {
                        /** 이 시험은 비용 비교 대신 응답 보상 경계를 검사한다. */
                        public boolean matches(CharSequence raw, String encoded) {
                            return true;
                        }

                        /** 비밀번호 변경/해시 생성은 실행하지 않는다. */
                        public String encode(CharSequence raw) {
                            throw new AssertionError("Not used");
                        }
                    };
            var key =
                    new RSAKey.Builder(new Base64URL("AQAB"), new Base64URL("AQAB"))
                            .keyID("synthetic-metadata-only")
                            .build();
            auth =
                    new AuthLoginService(
                            users,
                            states,
                            writer,
                            passwords,
                            null,
                            TestPolicies.local(),
                            parameters ->
                                    new Jwt(
                                            "synthetic-access",
                                            clock.instant(),
                                            clock.instant().plusSeconds(600),
                                            Map.of("alg", "RS256"),
                                            Map.of("sub", "1")),
                            null,
                            new SigningKeys(key, new JWKSet(key)),
                            clock,
                            json,
                            manager);
        }

        /** 실제 응답 projection의 Mapper 실패를 관찰한다. */
        AuthController controller() {
            return new AuthController(
                    auth,
                    new UserService(users, json),
                    new AuthCookies(TestPolicies.environment(), clock),
                    new CookieCsrfTokenRepository(),
                    json);
        }

        /** 공개 자격 예제가 아닌 합성 입력이다. */
        AuthRequests.Login request() {
            return new AuthRequests.Login("synthetic", "synthetic");
        }
    }

    /** 필요한 Mapper/store 동작만 실패 주입한다. */
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (target, method, args) -> invocation.call(method.getName(), args));
    }

    /** 합성 adapter의 안전한 호출 경계다. */
    private interface Invocation {
        Object call(String method, Object[] arguments);
    }
}
