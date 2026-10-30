package com.example.starter.audit.web;

import com.example.starter.audit.model.AuditQuery;
import com.example.starter.audit.model.AuditViews;
import com.example.starter.audit.service.AuditQueryService;
import com.example.starter.common.web.ApiIds;
import com.example.starter.common.web.ApiResponse;
import com.example.starter.common.web.ApiResponses;
import com.example.starter.common.web.ListQuery;
import com.example.starter.common.web.PageData;
import com.example.starter.common.web.UtcTimes;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 감사 필터와 공개 응답 allowlist만 연결한다. 원문 IP/UA는 반환하지 않는다. */
@RestController
@RequestMapping(value = "/api/audits", produces = "application/json")
public class AuditController {
    private final AuditQueryService service;

    /** 조회 자체 감사와 권한은 서비스 경계에서 수행한다. */
    public AuditController(AuditQueryService service) {
        this.service = service;
    }

    /** 필수 UTC from/to 기간 내 접근 이력을 읽는다. */
    @GetMapping("/access")
    @Operation(
            operationId = "searchAccessAudits",
            summary = "접근 이력 조회",
            description =
                    "AUDIT_READ, REQUIRED 자기 조회 이력. UTC 밀리초 from 포함/to 미포함, 최대31일, size 최대100.")
    public ResponseEntity<ApiResponse<PageData<AuditViews.Access>>> access(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceCode) {
        return ApiResponses.ok(
                service.access(
                        new AuditQuery(
                                UtcTimes.parseMillis(from),
                                UtcTimes.parseMillis(to),
                                new ListQuery(null, page, size, ListQuery.Sort.idAsc),
                                actorId == null ? null : ApiIds.parse(actorId),
                                resourceCode,
                                null,
                                null)));
    }

    /** 같은 기간 규칙으로 보안 이력만 읽는다. */
    @GetMapping("/security")
    @Operation(
            operationId = "searchSecurityAudits",
            summary = "보안 이력 조회",
            description =
                    "SECURITY_AUDIT_READ, REQUIRED 자기 조회 이력. UTC 밀리초 기간 최대31일. login"
                            + " reference·IP·UA는 제외합니다.")
    public ResponseEntity<ApiResponse<PageData<AuditViews.Security>>> security(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String eventCode,
            @RequestParam(required = false) String targetUserId) {
        return ApiResponses.ok(
                service.security(
                        new AuditQuery(
                                UtcTimes.parseMillis(from),
                                UtcTimes.parseMillis(to),
                                new ListQuery(null, page, size, ListQuery.Sort.idAsc),
                                null,
                                null,
                                eventCode,
                                targetUserId == null ? null : ApiIds.parse(targetUserId))));
    }
}
