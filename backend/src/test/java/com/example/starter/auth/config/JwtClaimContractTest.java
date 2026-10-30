package com.example.starter.auth.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.TestPolicies;
import com.example.starter.auth.model.AuthError;
import com.example.starter.auth.model.AuthLogin;
import com.example.starter.auth.model.AuthPermissions;
import com.example.starter.auth.store.AuthStateStore;
import com.example.starter.common.web.ApiException;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;

/** RSA 키 생성 없이 서명 후 적용되는 필수 claim 정책만 검사한다. */
class JwtClaimContractTest {
    private final Instant now = Instant.parse("2026-10-29T00:00:00Z");

    /** 외부 scope는 무시하고 공유 snapshot만 authority로 사용하며 손상은503이다. */
    @Test
    void authoritiesOnlyComeFromValidatedState() {
        String sid = "b5cf43c1-9585-43cd-b637-e458bb487956";
        var state =
                new AuthLogin(
                        sid,
                        Long.MAX_VALUE,
                        now.toEpochMilli(),
                        now.toEpochMilli(),
                        now.toEpochMilli(),
                        now.plusSeconds(1800).toEpochMilli(),
                        now.plusSeconds(28800).toEpochMilli(),
                        null,
                        null,
                        "[\"USER_READ_SELF\"]",
                        "synthetic-hash");
        var store =
                (AuthStateStore)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {AuthStateStore.class},
                                (proxy, method, arguments) -> state);
        var json = JsonMapper.builder().build();
        var converter =
                new AuthAuthenticationConverter(store, Clock.fixed(now, ZoneOffset.UTC), json);
        var token = valid().claim("sid", sid).claim("scope", "ROLE_ASSIGN").build();
        var authentication = converter.convert(token);
        Assertions.assertEquals(
                List.of("USER_READ_SELF"),
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList());
        Assertions.assertEquals(
                AuthError.TOKEN_REVOKED,
                Assertions.assertThrows(
                                ApiException.class,
                                () ->
                                        converter.convert(
                                                valid().claim("sid", UUID.randomUUID().toString())
                                                        .build()))
                        .error());
        Assertions.assertEquals(
                AuthError.AUTH_STATE_UNAVAILABLE,
                Assertions.assertThrows(
                                ApiException.class,
                                () -> AuthPermissions.parse(json, "[\"UNKNOWN_PERMISSION\"]"))
                        .error());
    }

    /** NumericDate·issuer/aud·sid/sub·typ·nbf·exp/skew 누락을 거부한다. */
    @Test
    void requiredClaimsAndTimePolicy() {
        var validator =
                JwtConfiguration.requiredClaims(
                        TestPolicies.local(), Clock.fixed(now, ZoneOffset.UTC));
        assertFalse(validator.validate(valid().build()).hasErrors());
        assertTrue(
                validator
                        .validate(
                                valid().expiresAt(now.minusSeconds(31))
                                        .issuedAt(now.minusSeconds(600))
                                        .claim("auth_time", now.minusSeconds(600).getEpochSecond())
                                        .build())
                        .hasErrors());
        assertTrue(validator.validate(valid().notBefore(now.plusSeconds(31)).build()).hasErrors());
        assertTrue(validator.validate(valid().header("typ", "JWT").build()).hasErrors());
        assertTrue(validator.validate(valid().header("alg", "HS256").build()).hasErrors());
        assertTrue(validator.validate(valid().audience(List.of("other")).build()).hasErrors());
        assertTrue(validator.validate(valid().subject("1.0").build()).hasErrors());
        assertTrue(validator.validate(valid().claim("asg", "old-generation").build()).hasErrors());
        assertTrue(validator.validate(valid().claim("sid", "bad").build()).hasErrors());
        assertTrue(validator.validate(valid().claim("auth_time", "123").build()).hasErrors());
    }

    /** 실제 서명을 수행하지 않는 validator 입력 모델이다. */
    private Jwt.Builder valid() {
        return Jwt.withTokenValue("synthetic-validator-input")
                .header("alg", "RS256")
                .header("typ", "at+jwt")
                .header("kid", "synthetic-kid")
                .claim("asg", "v1")
                .issuer("urn:starter:local")
                .audience(List.of("starter-api-local"))
                .subject("9223372036854775807")
                .jti(UUID.randomUUID().toString())
                .claim("sid", UUID.randomUUID().toString())
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plusSeconds(600))
                .claim("auth_time", now.getEpochSecond());
    }
}
