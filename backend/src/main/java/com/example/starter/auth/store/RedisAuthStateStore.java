package com.example.starter.auth.store;

import com.example.starter.auth.model.AuthError;
import com.example.starter.auth.model.AuthLogin;
import com.example.starter.common.config.AppProperties;
import com.example.starter.common.web.ApiException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 사용자 hash tag의 Lua 상태와 hashed locator만 사용하는 Redis 인증 저장소다. */
@Component
@ConditionalOnProperty(name = "app.auth.state-store", havingValue = "redis", matchIfMissing = true)
public class RedisAuthStateStore implements AuthStateStore {
    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final AppProperties.Auth policy;
    private final DefaultRedisScript<String> userScript;
    private final DefaultRedisScript<Long> attemptScript;

    /** 두 script는 외부 사용자 SQL·code를 받아 실행하지 않는다. */
    public RedisAuthStateStore(
            StringRedisTemplate redis, JsonMapper json, AppProperties properties) {
        this.redis = redis;
        this.json = json;
        this.policy = properties.auth();
        this.userScript = new DefaultRedisScript<>();
        this.userScript.setLocation(new ClassPathResource("auth/user-state.lua"));
        this.userScript.setResultType(String.class);
        this.attemptScript = new DefaultRedisScript<>();
        this.attemptScript.setLocation(new ClassPathResource("auth/attempt.lua"));
        this.attemptScript.setResultType(Long.class);
    }

    /** Lua 생성 후 locator 저장이 실패하면 부분 로그인을 폐기하고 발급을 거부한다. */
    @Override
    public boolean create(AuthLogin login) {
        boolean replaced =
                invoke(login.userId(), "create", login.createdAt(), login)
                        .get("replaced")
                        .asBoolean();

        try {
            locator(
                    "sid",
                    login.sid(),
                    login.userId() + "",
                    login.absoluteExpiresAt(),
                    login.createdAt());
            locator(
                    "refresh",
                    login.currentRefreshHash(),
                    login.userId() + "|" + login.sid(),
                    login.absoluteExpiresAt(),
                    login.createdAt());
        } catch (RuntimeException exception) {
            try {
                call(
                        login.userId(),
                        "revoke",
                        login.createdAt(),
                        Map.of("sid", login.sid(), "reason", "ISSUE_FAILED"));
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }

            throw new ApiException(AuthError.AUTH_STATE_UNAVAILABLE, exception);
        }

        return replaced;
    }

    /** locator를 신뢰하지 않고 최종 user Lua에서 active/fence를 검사한다. */
    @Override
    public AuthLogin require(String sid, long now) {
        String user = value("sid", sid);

        if (user == null) {
            throw new ApiException(AuthError.TOKEN_REVOKED);
        }

        return call(Long.parseLong(user), "require", now, Map.of("sid", sid));
    }

    /** CAS와 tombstone 변경은 한 Lua이며 다음 locator 실패는 새 상태도 폐기한다. */
    @Override
    public AuthLogin rotate(String oldHash, String newHash, long now) {
        String location = value("refresh", oldHash);

        if (location == null) {
            throw new ApiException(AuthError.TOKEN_REVOKED);
        }

        String[] parts = location.split("\\|", 2);
        long userId = Long.parseLong(parts[0]);
        AuthLogin login =
                call(
                        userId,
                        "rotate",
                        now,
                        Map.of("sid", parts[1], "oldHash", oldHash, "newHash", newHash));

        try {
            locator("refresh", newHash, location, login.absoluteExpiresAt(), now);
        } catch (RuntimeException exception) {
            try {
                call(userId, "revoke", now, Map.of("sid", parts[1], "reason", "ISSUE_FAILED"));
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }

            throw new ApiException(AuthError.AUTH_STATE_UNAVAILABLE, exception);
        }

        return login;
    }

    /** 알려진 locator는 폐기 대상 후보만 제공하고 인증을 허용하지 않는다. */
    @Override
    public String locateRefresh(String hash, long now) {
        String location = value("refresh", hash);

        if (location == null) {
            return null;
        }

        String[] parts = location.split("\\|", 2);

        try {
            return call(
                            Long.parseLong(parts[0]),
                            "locateRefresh",
                            now,
                            Map.of("sid", parts[1], "oldHash", hash))
                    .sid();
        } catch (ApiException failure) {
            if (failure.error() == AuthError.TOKEN_REVOKED) {
                return null;
            }

            throw failure;
        }
    }

    /** Lua에서 만료 검사와 MAX 갱신을 함께 수행한다. */
    @Override
    public void touch(String sid, long now) {
        String user = value("sid", sid);

        if (user == null) {
            throw new ApiException(AuthError.TOKEN_REVOKED);
        }

        call(Long.parseLong(user), "touch", now, Map.of("sid", sid));
    }

    /** 상태가 없으면 멱등 종료하며 locator로 임의 사용자 상태를 만들지 않는다. */
    @Override
    public void revoke(String sid, long now, String reason) {
        String user = value("sid", sid);

        if (user != null) {
            call(Long.parseLong(user), "revoke", now, Map.of("sid", sid, "reason", reason));
        }
    }

    /** fence가 있어도 복구 담당자의 전체 폐기를 허용한다. */
    @Override
    public void revokeAll(long userId, long now, String reason) {
        call(userId, "revokeAll", now, Map.of("reason", reason));
    }

    /** TTL 없는 operation 소유 차단을 설정한다. */
    @Override
    public void block(long userId, String operationId, long now) {
        call(userId, "block", now, Map.of("operationId", operationId));
    }

    /** 동일 operation만 영속 fence를 해제한다. */
    @Override
    public void unblock(long userId, String operationId, long now) {
        call(userId, "unblock", now, Map.of("operationId", operationId));
    }

    /** 단일 hashed bucket Lua로 다중 노드 요청을 제한한다. */
    @Override
    public long consume(String type, String hash, int maximum, long window, long now) {
        return attempt(type, hash, maximum, window, 0, now, "consume");
    }

    /** 계정 lock은 fixed window가 바뀌어도 해제하지 않는다. */
    @Override
    public long accountRetry(String hash, long now) {
        return attempt("ACCOUNT", hash, 1, 1, 0, now, "check");
    }

    /** 실패 횟수 증가와 임계치 lock을 한 script로 기록한다. */
    @Override
    public void failed(String hash, int maximum, long window, long lock, long now) {
        attempt("ACCOUNT", hash, maximum, window, lock, now, "consume");
    }

    /** 성공 계정의 실패만 지운다. */
    @Override
    public void clearAccount(String hash) {
        available(() -> redis.delete(bucket("ACCOUNT", hash)));
    }

    /** 시작 시 모든 노드의 정책과 검증 키 목록이 동일한지 확인한다. */
    @Override
    public void verifyPolicy(String digest) {
        available(
                () -> {
                    String key = policy.namespace() + ":policy";
                    redis.opsForValue().setIfAbsent(key, digest);

                    if (!digest.equals(redis.opsForValue().get(key))) {
                        throw new ApiException(AuthError.AUTH_STATE_UNAVAILABLE);
                    }

                    return null;
                });
    }

    /** 운영 복구의 세대 변경이 이전 snapshot의 locator/fence/login을 다시 사용하지 않게 한다. */
    private String physicalNamespace() {
        return policy.namespace() + ":" + policy.namespaceGeneration();
    }

    /** script의 안전한 등록 코드만 예외로 변환한다. */
    private AuthLogin call(long userId, String command, long now, Object payload) {
        var result = invoke(userId, command, now, payload);

        return result.has("sid") ? json.readValue(result.toString(), AuthLogin.class) : null;
    }

    /** Lua 결과의 등록 오류만 예외로 변환한다. */
    private JsonNode invoke(long userId, String command, long now, Object payload) {
        return available(
                () -> {
                    String result =
                            redis.execute(
                                    userScript,
                                    List.of(physicalNamespace() + ":{" + userId + "}:state"),
                                    command,
                                    Long.toString(now),
                                    json.writeValueAsString(payload),
                                    policy.concurrentLogin().name(),
                                    Long.toString(policy.idleTimeout().toMillis()));

                    if (result == null) {
                        throw new ApiException(AuthError.AUTH_STATE_UNAVAILABLE);
                    }

                    var tree = json.readTree(result);

                    if (tree.has("error")) {
                        throw new ApiException(AuthError.valueOf(tree.get("error").asString()));
                    }

                    return tree;
                });
    }

    /** locator는 최종 user script와 별개로 저장하고 절대 만료에 맞춘다. */
    private void locator(String type, String secret, String location, long expiry, long now) {
        available(
                () -> {
                    redis.opsForValue()
                            .set(
                                    key(type, secret),
                                    location,
                                    Duration.ofMillis(Math.max(1, expiry - now)));

                    return null;
                });
    }

    /** sid 자체도 locator key에 직접 넣지 않는다. */
    private String key(String type, String secret) {
        return physicalNamespace() + ":locator:" + type + ":" + TokenSecrets.hash(secret);
    }

    /** 저장소 실패와 존재하지 않는 locator를 구분한다. */
    private String value(String type, String secret) {
        return available(() -> redis.opsForValue().get(key(type, secret)));
    }

    /** 원문 계정·IP가 Redis key에 들어가지 않게 한다. */
    private String bucket(String type, String hash) {
        return physicalNamespace() + ":attempt:" + type + ":" + hash;
    }

    /** rate script가 반환하는 대기 초를 읽는다. */
    private long attempt(
            String type,
            String hash,
            int maximum,
            long window,
            long lock,
            long now,
            String action) {
        return available(
                () -> {
                    Long result =
                            redis.execute(
                                    attemptScript,
                                    List.of(bucket(type, hash)),
                                    Long.toString(now),
                                    Integer.toString(maximum),
                                    Long.toString(window),
                                    Long.toString(lock),
                                    action);

                    if (result == null) {
                        throw new ApiException(AuthError.AUTH_STATE_UNAVAILABLE);
                    }

                    return result;
                });
    }

    /** Redis 장애에서 JDBC나 메모리로 fallback하지 않는다. */
    private <T> T available(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ApiException(AuthError.AUTH_STATE_UNAVAILABLE, exception);
        }
    }
}
