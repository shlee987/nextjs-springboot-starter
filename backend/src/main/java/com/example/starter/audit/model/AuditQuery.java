package com.example.starter.audit.model;

import com.example.starter.common.web.ApiError;
import com.example.starter.common.web.ApiException;
import com.example.starter.common.web.ListQuery;
import java.time.Duration;
import java.time.Instant;

/** 감사 조회 기간과 내부 식별자만 허용하고 검색어 원문을 기록하지 않는다. */
public record AuditQuery(
        Instant from,
        Instant to,
        ListQuery page,
        Long actorId,
        String resourceCode,
        String eventCode,
        Long targetUserId) {
    /** 조회 기간은 31일 이내이고 코드 필터는 고정 ASCII 범위다. */
    public AuditQuery {
        if (from == null
                || to == null
                || !from.isBefore(to)
                || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0
                || resourceCode != null && !resourceCode.matches("[A-Z_]{1,64}")
                || eventCode != null && !eventCode.matches("[A-Z_]{1,64}")) {
            throw new ApiException(ApiError.VALIDATION_FAILED);
        }
    }
}
