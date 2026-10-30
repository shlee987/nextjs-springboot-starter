package com.example.starter.audit.model;

import java.time.Instant;

/** append-only 감사 INSERT에 필요한 고정 코드·내부 식별자와 영속 전용 직접 peer를 포함한다. */
public record AuditEvent(
        String id,
        Instant occurredAt,
        Long actorUserId,
        String actorKind,
        String resourceCode,
        String actionCode,
        String targetType,
        String targetId,
        String outcome,
        String reasonCode,
        String traceId,
        String serverId,
        String operationId,
        String eventCode,
        Long targetUserId,
        String loginReference,
        String clientIp) {
    /** 영속 전용 peer를 진단 문자열에 노출하지 않는다. */
    @Override
    public String toString() {
        return "AuditEvent[redacted]";
    }
}
