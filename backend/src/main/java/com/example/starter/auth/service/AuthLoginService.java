package com.example.starter.auth.service;

import com.example.starter.audit.service.AuditWriter;
import com.example.starter.auth.config.SigningKeys;
import com.example.starter.auth.model.AuthContext;
import com.example.starter.auth.model.AuthError;
import com.example.starter.auth.model.AuthLogin;
import com.example.starter.auth.model.IssuedTokens;
import com.example.starter.auth.store.AuthStateStore;
import com.example.starter.auth.store.TokenSecrets;
import com.example.starter.auth.web.AuthRequests;
import com.example.starter.common.config.AppProperties;
import com.example.starter.common.web.ApiError;
import com.example.starter.common.web.ApiException;
import com.example.starter.permission.Permission;
import com.example.starter.user.mapper.UserMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** 자격 확인→최신 DB 상태 잠금→원자 상태 생성→감사 확인→쿠키 발급 결과를 소유한다. */
@Service
public class AuthLoginService {
    private static final String DUMMY =
            "{argon2}$argon2id$v=19$m=19456,t=2,p=1$xEIADau0nITStXj1om5P2g$VtAhVuavHavD6NUrNs/l/2qmT0CK8TQuVYmO4eXzeOk";
    private static final Set<String> WEAK =
            Set.of(
                    "LocalExample1234!",
                    "Password1234567!",
                    "123456789012345",
                    "passwordpassword",
                    "correct horse battery staple");
    private final UserMapper users;
    private final AuthStateStore states;
    private final AuditWriter audit;
    private final PasswordEncoder passwords;
    private final AuthInvalidationService invalidation;
    private final AppProperties.Auth policy;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final SigningKeys keys;
    private final Clock clock;
    private final JsonMapper json;
    private final TransactionTemplate transaction;

    /** 비용이 큰 hash 비교는 transaction을 시작하기 전에 수행한다. */
    public AuthLoginService(
            UserMapper users,
            AuthStateStore states,
            AuditWriter audit,
            PasswordEncoder passwords,
            AuthInvalidationService invalidation,
            AppProperties properties,
            JwtEncoder encoder,
            JwtDecoder decoder,
            SigningKeys keys,
            Clock clock,
            JsonMapper json,
            PlatformTransactionManager manager) {
        this.users = users;
        this.states = states;
        this.audit = audit;
        this.passwords = passwords;
        this.invalidation = invalidation;
        this.policy = properties.auth();
        this.encoder = encoder;
        this.decoder = decoder;
        this.keys = keys;
        this.clock = clock;
        this.json = json;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setTimeout(5);
    }

    /** 미존재/비활성 사용자 모두 같은 비용 비교와 LOGIN_FAILED를 사용한다. */
    public IssuedTokens login(AuthRequests.Login request, String clientIp) {
        return login(request, clientIp, Function.identity());
    }

    /** 응답 projection까지 완료한 뒤만 발급 결과를 반환하며 실패하면 새 sid를 폐기한다. */
    public <T> T login(
            AuthRequests.Login request, String clientIp, Function<IssuedTokens, T> projection) {
        long now = clock.millis();
        var limits = policy.failedLogin();
        String accountHash = TokenSecrets.hash(request.loginId());
        limited(
                states.consume(
                        "IP",
                        TokenSecrets.hash(clientIp),
                        limits.ipMaxAttempts(),
                        limits.ipWindow().toMillis(),
                        now));
        limited(states.accountRetry(accountHash, now));
        var user = users.selectAccountByLoginId(request.loginId());
        String hash = user == null || user.passwordHash() == null ? DUMMY : user.passwordHash();
        boolean matches = credentialMatches(request.password(), hash);

        if (!matches
                || user == null
                || !"ACTIVE".equals(user.status())
                || user.passwordHash() == null) {
            states.failed(
                    accountHash,
                    limits.maxAttempts(),
                    limits.window().toMillis(),
                    limits.lockDuration().toMillis(),
                    now);
            audit.security("LOGIN_FAILED", null, "FAILURE", "LOGIN_FAILED");

            throw new ApiException(AuthError.LOGIN_FAILED);
        }

        String refresh = TokenSecrets.refresh();
        String sid = UUID.randomUUID().toString();

        try {
            AuthLogin login =
                    transaction.execute(
                            status -> {
                                users.selectUserForUpdate(user.userId());
                                var latest = users.selectAccount(user.userId());

                                if (latest == null
                                        || !"ACTIVE".equals(latest.status())
                                        || !hash.equals(latest.passwordHash())) {
                                    throw new ApiException(AuthError.LOGIN_FAILED);
                                }

                                long created = clock.millis();
                                var state =
                                        new AuthLogin(
                                                sid,
                                                user.userId(),
                                                created,
                                                created,
                                                created,
                                                created + policy.idleTimeout().toMillis(),
                                                created + policy.absoluteTimeout().toMillis(),
                                                null,
                                                null,
                                                json.writeValueAsString(
                                                        Permission.registered(
                                                                users.selectPermissionList(
                                                                        user.userId()))),
                                                refreshHash(refresh));
                                if (states.create(state)) {
                                    audit.securityInTransaction(
                                            "CONCURRENT_LOGIN_REPLACED", user.userId());
                                }

                                return state;
                            });
            // 별도 감사 commit이 실패하면 이미 생성된 로그인도 폐기한다.
            audit.security("LOGIN_SUCCESS", login.userId(), "SUCCESS", null, login.sid());
            states.clearAccount(accountHash);

            return projection.apply(issue(login, refresh));
        } catch (RuntimeException failure) {
            revokePartial(sid, failure);

            if (failure instanceof ApiException api
                    && api.error() == AuthError.CONCURRENT_LOGIN_DENIED) {
                audit.security(
                        "CONCURRENT_LOGIN_DENIED",
                        user.userId(),
                        "DENIED",
                        "CONCURRENT_LOGIN_DENIED");
            }

            throw failure;
        }
    }

    /** refresh는 활동 시간을 연장하지 않고 회전된 자격만 쿠키 경계로 반환한다. */
    public IssuedTokens refresh(String raw) {
        if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}")) {
            throw new ApiException(AuthError.TOKEN_REVOKED);
        }

        String hash = refreshHash(raw);
        String next = TokenSecrets.refresh();
        AuthLogin login;

        try {
            login = states.rotate(hash, refreshHash(next), clock.millis());
        } catch (ApiException failure) {
            if (failure.error() == AuthError.TOKEN_REFRESH_REUSED) {
                audit.security(
                        "TOKEN_REUSED",
                        null,
                        "FAILURE",
                        "TOKEN_REFRESH_REUSED",
                        states.locateRefresh(hash, clock.millis()));
            }

            throw failure;
        }

        try {
            audit.security("TOKEN_REFRESHED", login.userId(), "SUCCESS", null, login.sid());

            return issue(login, next);
        } catch (RuntimeException failure) {
            revokePartial(login.sid(), failure);

            throw failure;
        }
    }

    /** 무효/없는 refresh도 같은 성공 의미이며 유효한 후보만 현재 sid를 폐기한다. */
    public void logout(String raw, String access) {
        String sid =
                raw != null && raw.matches("[A-Za-z0-9_-]{43}")
                        ? states.locateRefresh(refreshHash(raw), clock.millis())
                        : null;

        if (sid == null && access != null) {
            try {
                var jwt = decoder.decode(access);
                var login = states.require(jwt.getClaimAsString("sid"), clock.millis());

                if (Long.toString(login.userId()).equals(jwt.getSubject())) {
                    sid = login.sid();
                }
            } catch (JwtException invalid) {
                // 무효 access는 logout의 동일 204 계약을 유지한다.
            } catch (ApiException invalid) {
                if (invalid.error() != AuthError.TOKEN_REVOKED) {
                    throw invalid;
                }
            }
        }

        if (sid != null) {
            states.revoke(sid, clock.millis(), "LOGOUT");
        }

        audit.security("LOGOUT", null, "SUCCESS", null, sid);
    }

    /** 검증된 본인의 모든 기기를 먼저 폐기하고 이력을 남긴다. */
    public void logoutAll() {
        long userId = AuthContext.require().userId();
        states.revokeAll(userId, clock.millis(), "LOGOUT_ALL");
        audit.security("LOGOUT_ALL", userId, "SUCCESS", null);
    }

    /** 기존 credential 확인과 새 hash 계산은 잠금 밖에서 끝낸다. */
    public void password(AuthRequests.Password request) {
        long userId = AuthContext.require().userId();
        var user = users.selectAccount(userId);

        if (user == null || user.passwordHash() == null) {
            throw new ApiException(AuthError.PASSWORD_LOGIN_UNAVAILABLE);
        }

        if (!credentialMatches(request.currentPassword(), user.passwordHash())) {
            throw new ApiException(AuthError.CURRENT_PASSWORD_INVALID);
        }

        if (credentialMatches(request.newPassword(), user.passwordHash())) {
            throw new ApiException(AuthError.PASSWORD_UNCHANGED);
        }

        if (WEAK.contains(request.newPassword())) {
            throw new ApiException(ApiError.VALIDATION_FAILED);
        }

        invalidation.changePassword(
                userId, user.passwordHash(), passwords.encode(request.newPassword()));
    }

    /** 복구 전환 뒤 old refresh가 JDBC snapshot과 일치하지 않도록 세대를 용도 분리한다. */
    private String refreshHash(String raw) {
        return TokenSecrets.hash(
                policy.namespace() + ":" + policy.namespaceGeneration() + ":" + raw);
    }

    /** 손상/미지원 저장 hash도 dummy Argon2 비교 후 통일된 자격 실패로 처리한다. */
    private boolean credentialMatches(String raw, String encoded) {
        if (!encoded.startsWith("{argon2}$argon2id$")) {
            passwords.matches(raw, DUMMY);

            return false;
        }

        try {
            return passwords.matches(raw, encoded);
        } catch (IllegalArgumentException invalidStoredHash) {
            passwords.matches(raw, DUMMY);

            return false;
        }
    }

    /** JWT에는 권한·이메일을 넣지 않고 NumericDate 표준을 유지한다. */
    private IssuedTokens issue(AuthLogin login, String refresh) {
        states.require(login.sid(), clock.millis());
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant absolute = Instant.ofEpochMilli(login.absoluteExpiresAt());
        Instant expires = now.plus(policy.accessTtl());

        if (expires.isAfter(absolute)) {
            expires = absolute.truncatedTo(ChronoUnit.SECONDS);
        }

        var claims =
                JwtClaimsSet.builder()
                        .issuer(policy.jwt().issuer())
                        .audience(List.of(policy.jwt().audience()))
                        .subject(Long.toString(login.userId()))
                        .id(UUID.randomUUID().toString())
                        .issuedAt(now)
                        .notBefore(now)
                        .expiresAt(expires)
                        .claim("sid", login.sid())
                        .claim("asg", policy.namespaceGeneration())
                        .claim("auth_time", login.authenticatedAt() / 1000)
                        .build();
        var header =
                JwsHeader.with(SignatureAlgorithm.RS256)
                        .keyId(keys.active().getKeyID())
                        .type("at+jwt")
                        .build();
        String access = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new IssuedTokens(
                access,
                refresh,
                expires,
                absolute,
                login.userId(),
                login.sid(),
                login.permissionsJson());
    }

    /** 발급 실패가 새로 저장된 자격을 정상 로그인으로 남기지 않게 한다. */
    private void revokePartial(String sid, RuntimeException failure) {
        try {
            states.revoke(sid, clock.millis(), "ISSUE_FAILED");
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    /** 공개 대기 초만 남기며 raw 계정/IP를 이력에 저장하지 않는다. */
    private void limited(long retry) {
        if (retry > 0) {
            audit.security("LOGIN_LIMITED", null, "DENIED", "RATE_LIMITED");

            throw new ApiException(ApiError.RATE_LIMITED, retry);
        }
    }
}
