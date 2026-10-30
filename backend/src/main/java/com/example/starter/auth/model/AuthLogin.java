package com.example.starter.auth.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.security.Principal;

/** HTTP에 노출하지 않는 공유 로그인 상태다. 시각은 내부 원자 비교용 epoch 밀리초다. */
public record AuthLogin(
        String sid,
        @JsonFormat(shape = Shape.STRING) long userId,
        long createdAt,
        long authenticatedAt,
        long lastActivityAt,
        long idleExpiresAt,
        long absoluteExpiresAt,
        Long revokedAt,
        String revokeReason,
        String permissionsJson,
        String currentRefreshHash)
        implements Principal {
    /** 표준 principal 이름도 raw sid 대신 내부 사용자 숫자 문자열로 제공한다. */
    @JsonIgnore
    @Override
    public String getName() {
        return Long.toString(userId);
    }

    /** 상태 모델이 진단 경로에서 raw sid·hash를 출력하지 않게 한다. */
    @Override
    public String toString() {
        return "AuthLogin[redacted]";
    }

    /** 청소 job과 관계없이 요청 시점에 만료·폐기를 판단한다. */
    public boolean isActive(long now) {
        return revokedAt == null && now < idleExpiresAt && now < absoluteExpiresAt;
    }
}
