package com.example.starter.auth.model;

import java.time.Instant;

/** 서비스→쿠키 경계 전용 결과다. Controller는 이 객체를 JSON에 반환하지 않는다. */
public record IssuedTokens(
        String access,
        String refresh,
        Instant accessExpiresAt,
        Instant absoluteExpiresAt,
        long userId,
        String sid,
        String permissionsJson) {
    /** 원문 token이 로깅되거나 기본 진단에 노출되지 않게 한다. */
    @Override
    public String toString() {
        return "IssuedTokens[redacted]";
    }
}
