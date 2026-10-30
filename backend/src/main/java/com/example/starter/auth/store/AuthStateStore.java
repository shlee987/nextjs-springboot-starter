package com.example.starter.auth.store;

import com.example.starter.auth.model.AuthLogin;

/** Redis와 JDBC가 제공하는 사용자 단위 원자 상태 계약이다. memory fallback은 없다. */
public interface AuthStateStore {
    /** 원자 정책 검사 후 저장하고 실제 기존 활성 로그인 교체 여부를 반환한다. */
    boolean create(AuthLogin login);

    /** sid와 실제 사용자 상태를 확인하며 누락·만료·차단은 거부한다. */
    AuthLogin require(String sid, long now);

    /** 해시 CAS와 사용 이력을 한 번에 기록한다. 재사용은 해당 sid를 폐기한다. */
    AuthLogin rotate(String oldHash, String newHash, long now);

    /** 알려진 refresh만 sid로 변환하며 무작위 값으로 다른 로그인을 폐기하지 않는다. */
    String locateRefresh(String hash, long now);

    /** 유효 상태만 단조 증가시키고 만료된 상태는 부활시키지 않는다. */
    void touch(String sid, long now);

    /** 현재 sid를 폐기하며 tombstone은 절대 만료까지 보존한다. */
    void revoke(String sid, long now, String reason);

    /** 한 사용자의 모든 sid를 폐기한다. */
    void revokeAll(long userId, long now, String reason);

    /** 변경 operation 소유 영속 fence를 설정한다. 기존 타 소유 fence는 거부한다. */
    void block(long userId, String operationId, long now);

    /** 모든 폐기 확인 후에만 동일 operation 소유 fence를 해제한다. */
    void unblock(long userId, String operationId, long now);

    /** 고정 window 소비 결과의 Retry-After 초. 허용이면 0이다. */
    long consume(String type, String keyHash, int maximum, long windowMillis, long now);

    /** 계정 잠금이 남은 초. 로그인 자격 비교 전에 조회한다. */
    long accountRetry(String accountHash, long now);

    /** 실패 계정 한도를 증가시키고 임계치에서 lock 수명을 설정한다. */
    void failed(String accountHash, int maximum, long windowMillis, long lockMillis, long now);

    /** 계정 성공은 계정 실패만 지우고 IP 한도를 유지한다. */
    void clearAccount(String accountHash);

    /** 노드 간 키·issuer·정책 차이가 있으면 시작 준비를 실패시킨다. */
    void verifyPolicy(String digest);
}
