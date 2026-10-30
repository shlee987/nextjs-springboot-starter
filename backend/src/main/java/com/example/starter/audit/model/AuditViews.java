package com.example.starter.audit.model;

import java.time.Instant;

/** 원문 IP·UA·로그인 지문을 제외한 감사 API allowlist다. */
public final class AuditViews {
    /** DTO namespace의 인스턴스 생성을 막는다. */
    private AuditViews() {}

    /** 접근 이력의 공개 필드다. */
    public record Access(
            String auditId,
            Instant occurredAt,
            String actorUserId,
            String resourceCode,
            String actionCode,
            String targetType,
            String targetId,
            String outcome,
            String reasonCode,
            String traceId) {}

    /** 보안 이력의 공개 필드다. */
    public record Security(
            String eventId,
            Instant occurredAt,
            String eventCode,
            String actorUserId,
            String targetUserId,
            String outcome,
            String reasonCode,
            String traceId) {}
}
