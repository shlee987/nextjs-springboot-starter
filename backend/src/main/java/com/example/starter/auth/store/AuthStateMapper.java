package com.example.starter.auth.store;

import com.example.starter.auth.model.AuthLogin;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** JDBC 인증 상태만 접근하며 모든 user 변경은 guard 잠금 하에서 호출한다. */
@Mapper
public interface AuthStateMapper {
    /** 초기 정책 등록을 모든 노드에서 직렬화한다. */
    String selectPolicyLock();

    /** reserved epoch bucket의 정책만 조회한다. */
    List<String> selectPolicyList();

    /** 정리되지 않는 reserved 정책 bucket을 기록한다. */
    int insertPolicy(String digest);

    /** 사용자 guard가 없을 때 생성한다. */
    int insertGuard(@Param("userId") long userId, @Param("now") long now);

    /** 사용자별 상태 변경을 직렬화하고 fence 소유자를 반환한다. */
    String selectGuardForUpdate(long userId);

    /** sid의 현재 상태를 읽는다. */
    AuthLogin selectLogin(String sid);

    /** refresh 현재/과거 해시의 sid를 읽는다. */
    String selectSidByHash(String hash);

    /** 활성 로그인 수를 guard 잠금 하에서 계산한다. */
    long selectActiveCount(@Param("userId") long userId, @Param("now") long now);

    /** 새 로그인 상태를 기록한다. */
    int insertLogin(AuthLogin login);

    /** 사용한 hash tombstone을 기록한다. */
    int insertHistory(
            @Param("hash") String hash,
            @Param("sid") String sid,
            @Param("now") long now,
            @Param("expiry") long expiry);

    /** 단회 hash CAS를 수행한다. */
    int updateRefresh(
            @Param("sid") String sid,
            @Param("oldHash") String oldHash,
            @Param("newHash") String newHash);

    /** 활성 상태만 MAX로 touch한다. */
    int updateActivity(
            @Param("sid") String sid, @Param("now") long now, @Param("idleMillis") long idleMillis);

    /** sid를 폐기한다. */
    int updateRevoked(
            @Param("sid") String sid, @Param("now") long now, @Param("reason") String reason);

    /** 사용자 전체 sid를 폐기한다. */
    int updateAllRevoked(
            @Param("userId") long userId, @Param("now") long now, @Param("reason") String reason);

    /** fence 소유자를 기록하거나 해제한다. */
    int updateFence(
            @Param("userId") long userId,
            @Param("operationId") String operationId,
            @Param("now") long now);

    /** bucket 원자 증가와 임계치 잠금 후 제한까지 남은 초를 반환한다. */
    Long selectConsumedBucket(
            @Param("type") String type,
            @Param("hash") String hash,
            @Param("maximum") int maximum,
            @Param("window") long window,
            @Param("lock") long lock,
            @Param("now") long now);

    /** 현재 계정 잠금만 확인한다. */
    Long selectAccountRetry(@Param("hash") String hash, @Param("now") long now);

    /** 성공한 계정의 실패 bucket만 정리한다. */
    int deleteAccount(String hash);

    /** 절대 만료 tombstone을 batch 정리한다. */
    int deleteExpiredLogins(long now);

    /** 만료된 제한 bucket을 batch 정리한다. */
    int deleteExpiredBuckets(long now);
}
