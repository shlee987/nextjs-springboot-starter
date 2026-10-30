package com.example.starter.user.model;

import java.time.Instant;

/** 인증과 변경의 DB 조회 모델이다. 공개 응답으로 반환하지 않는다. */
public record UserAccount(
        long userId,
        String loginId,
        String displayName,
        String email,
        String status,
        Instant createdAt,
        Instant updatedAt,
        long version,
        String passwordHash) {
    /** 비밀번호 hash가 진단 문자열로 출력되지 않게 한다. */
    @Override
    public String toString() {
        return "UserAccount[redacted]";
    }
}
