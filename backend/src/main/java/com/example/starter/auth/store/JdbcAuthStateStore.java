package com.example.starter.auth.store;

import com.example.starter.auth.model.AuthError;
import com.example.starter.auth.model.AuthLogin;
import com.example.starter.common.config.AppProperties;
import com.example.starter.common.error.ErrorCode;
import com.example.starter.common.web.ApiException;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** guard 행 잠금과 동일 DataSource 트랜잭션으로 JDBC 인증 변경을 직렬화한다. */
@Component
@ConditionalOnProperty(name = "app.auth.state-store", havingValue = "jdbc")
public class JdbcAuthStateStore implements AuthStateStore {
    private final AuthStateMapper mapper;
    private final TransactionTemplate transaction;
    private final AppProperties.Auth policy;

    /** 업무 변경과 함께 참여하는 짧은 REQUIRED 트랜잭션을 구성한다. */
    public JdbcAuthStateStore(
            AuthStateMapper mapper, PlatformTransactionManager manager, AppProperties properties) {
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setTimeout(3);
        this.policy = properties.auth();
    }

    /** 최신 fence와 동시 정책을 사용자 guard 안에서 검사한 뒤 저장한다. */
    @Override
    public boolean create(AuthLogin login) {
        return execute(
                () -> {
                    guard(login.userId(), login.createdAt(), null);
                    long count = mapper.selectActiveCount(login.userId(), login.createdAt());

                    if (count > 0
                            && policy.concurrentLogin() == AppProperties.ConcurrentLogin.DENY_NEW) {
                        throw new ApiException(AuthError.CONCURRENT_LOGIN_DENIED);
                    }

                    if (policy.concurrentLogin() == AppProperties.ConcurrentLogin.REPLACE_OLD) {
                        mapper.updateAllRevoked(login.userId(), login.createdAt(), "REPLACED");
                    }

                    mapper.insertLogin(login);

                    return count > 0
                            && policy.concurrentLogin()
                                    == AppProperties.ConcurrentLogin.REPLACE_OLD;
                });
    }

    /** 사용자 guard와 sid 유효성을 함께 확인한다. */
    @Override
    public AuthLogin require(String sid, long now) {
        return execute(
                () -> {
                    AuthLogin login = requiredLogin(sid);
                    guard(login.userId(), now, null);
                    login = requiredLogin(sid);
                    active(login, now);

                    return login;
                });
    }

    /** 재사용 폐기는 commit한 다음 오류를 던져 rollback으로 부활하지 않게 한다. */
    @Override
    public AuthLogin rotate(String oldHash, String newHash, long now) {
        Rotation result =
                execute(
                        () -> {
                            String sid = mapper.selectSidByHash(oldHash);

                            if (sid == null) {
                                return new Rotation(null, AuthError.TOKEN_REVOKED);
                            }

                            AuthLogin login = requiredLogin(sid);
                            guard(login.userId(), now, null);
                            login = requiredLogin(sid);

                            if (now >= login.absoluteExpiresAt()) {
                                return new Rotation(null, AuthError.TOKEN_REVOKED);
                            }

                            if (!Objects.equals(login.currentRefreshHash(), oldHash)) {
                                mapper.updateRevoked(sid, now, "REFRESH_REUSED");

                                return new Rotation(null, AuthError.TOKEN_REFRESH_REUSED);
                            }

                            active(login, now);
                            mapper.insertHistory(oldHash, sid, now, login.absoluteExpiresAt());

                            if (mapper.updateRefresh(sid, oldHash, newHash) != 1) {
                                throw new ApiException(AuthError.AUTH_STATE_UNAVAILABLE);
                            }

                            return new Rotation(mapper.selectLogin(sid), null);
                        });

        if (result.error() != null) {
            throw new ApiException(result.error());
        }

        return result.login();
    }

    /** 알려진 현재/과거 hash의 sid만 찾는다. */
    @Override
    public String locateRefresh(String hash, long now) {
        return execute(() -> mapper.selectSidByHash(hash));
    }

    /** guard 하에서 아직 살아 있는 상태만 단조 증가시킨다. */
    @Override
    public void touch(String sid, long now) {
        execute(
                () -> {
                    AuthLogin login = requiredLogin(sid);
                    guard(login.userId(), now, null);

                    if (mapper.updateActivity(sid, now, policy.idleTimeout().toMillis()) != 1) {
                        throw new ApiException(AuthError.TOKEN_REVOKED);
                    }

                    return null;
                });
    }

    /** sid가 이미 없으면 멱등 종료하고 폐기된 상태는 복원하지 않는다. */
    @Override
    public void revoke(String sid, long now, String reason) {
        execute(
                () -> {
                    AuthLogin login = mapper.selectLogin(sid);

                    if (login != null) {
                        lock(login.userId(), now);
                        mapper.updateRevoked(sid, now, reason);
                    }

                    return null;
                });
    }

    /** fence가 있어도 복구 폐기를 허용한다. */
    @Override
    public void revokeAll(long userId, long now, String reason) {
        execute(
                () -> {
                    lock(userId, now);
                    mapper.updateAllRevoked(userId, now, reason);

                    return null;
                });
    }

    /** 같은 operation의 재진입만 허용한다. */
    @Override
    public void block(long userId, String operationId, long now) {
        execute(
                () -> {
                    guard(userId, now, operationId);
                    mapper.updateFence(userId, operationId, now);

                    return null;
                });
    }

    /** 타 operation의 차단을 해제할 수 없다. */
    @Override
    public void unblock(long userId, String operationId, long now) {
        execute(
                () -> {
                    String current = lock(userId, now);

                    if (!operationId.equals(current)) {
                        throw new ApiException(AuthError.AUTH_STATE_UNAVAILABLE);
                    }

                    mapper.updateFence(userId, null, now);

                    return null;
                });
    }

    /** fixed window bucket 증가를 DB ON CONFLICT 행 잠금에 위임한다. */
    @Override
    public long consume(String type, String hash, int maximum, long window, long now) {
        return execute(() -> mapper.selectConsumedBucket(type, hash, maximum, window, 0, now));
    }

    /** 마지막 실패 잠금 수명을 고정 window 경계와 독립적으로 확인한다. */
    @Override
    public long accountRetry(String hash, long now) {
        return execute(() -> mapper.selectAccountRetry(hash, now));
    }

    /** 다섯 번째 실패부터 독립 lock duration을 유지한다. */
    @Override
    public void failed(String hash, int maximum, long window, long lock, long now) {
        execute(() -> mapper.selectConsumedBucket("ACCOUNT", hash, maximum, window, lock, now));
    }

    /** 성공 계정의 bucket만 제거한다. */
    @Override
    public void clearAccount(String hash) {
        execute(() -> mapper.deleteAccount(hash));
    }

    /** reserved bucket에 정책 fingerprint를 영속 기록하고 다른 노드를 거부한다. */
    @Override
    public void verifyPolicy(String digest) {
        execute(
                () -> {
                    mapper.selectPolicyLock();
                    var digests = mapper.selectPolicyList();

                    if (!digests.isEmpty()
                            && (digests.size() != 1 || !digests.getFirst().equals(digest))) {
                        throw new ApiException(AuthError.AUTH_STATE_UNAVAILABLE);
                    }

                    mapper.insertPolicy(digest);

                    return null;
                });
    }

    /** DB 원문 오류를 공개 실패로 변환하고 fallback하지 않는다. */
    private <T> T execute(Supplier<T> operation) {
        try {
            return transaction.execute(status -> operation.get());
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ApiException(AuthError.AUTH_STATE_UNAVAILABLE, exception);
        }
    }

    /** 사용자 guard를 생성하고 잠금을 획득한다. */
    private String lock(long userId, long now) {
        mapper.insertGuard(userId, now);

        return mapper.selectGuardForUpdate(userId);
    }

    /** 현재 변경 소유자 외의 로그인·갱신을 차단한다. */
    private void guard(long userId, long now, String operationId) {
        String current = lock(userId, now);

        if (current == null || !current.isEmpty() && !current.equals(operationId)) {
            throw new ApiException(AuthError.AUTH_STATE_UNAVAILABLE);
        }
    }

    /** 저장되지 않은 sid로 상태를 재생성하지 않는다. */
    private AuthLogin requiredLogin(String sid) {
        AuthLogin login = mapper.selectLogin(sid);

        if (login == null) {
            throw new ApiException(AuthError.TOKEN_REVOKED);
        }

        return login;
    }

    /** DB 정리 시점에 기대지 않고 수명을 판단한다. */
    private static void active(AuthLogin login, long now) {
        if (!login.isActive(now)) {
            throw new ApiException(AuthError.TOKEN_REVOKED);
        }
    }

    /** 폐기를 commit한 뒤 전달할 안전한 회전 결과다. */
    private record Rotation(AuthLogin login, ErrorCode error) {}
}
