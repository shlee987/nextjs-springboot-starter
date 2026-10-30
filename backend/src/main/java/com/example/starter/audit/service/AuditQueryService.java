package com.example.starter.audit.service;

import com.example.starter.audit.mapper.AuditMapper;
import com.example.starter.audit.model.AuditQuery;
import com.example.starter.audit.model.AuditViews;
import com.example.starter.common.config.AppProperties;
import com.example.starter.common.web.ApiError;
import com.example.starter.common.web.ApiException;
import com.example.starter.common.web.PageData;
import java.time.Duration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 감사 조회 자체도 필수 접근 이벤트로 기록하되 low-level INSERT에는 재귀하지 않는다. */
@Service
public class AuditQueryService {
    private final AuditMapper mapper;
    private final int maxDays;

    /** 조회 상한을 환경 정책으로 제한한다. */
    public AuditQueryService(AuditMapper mapper, AppProperties properties) {
        this.mapper = mapper;
        this.maxDays = properties.audit().query().maxRangeDays();
    }

    /** 공개 allowlist 접근 이력만 조회한다. */
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @AccessAudit(
            resource = AccessAudit.Resource.ACCESS_AUDIT,
            action = AccessAudit.Action.READ,
            policy = AccessAudit.Policy.REQUIRED)
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public PageData<AuditViews.Access> access(AuditQuery query) {
        validate(query);

        return PageData.of(
                mapper.selectAccessList(query),
                query.page().page(),
                query.page().size(),
                mapper.selectAccessCount(query));
    }

    /** 별도 보안 이력 권한을 적용한다. */
    @PreAuthorize("hasAuthority('SECURITY_AUDIT_READ')")
    @AccessAudit(
            resource = AccessAudit.Resource.SECURITY_AUDIT,
            action = AccessAudit.Action.READ,
            policy = AccessAudit.Policy.REQUIRED)
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public PageData<AuditViews.Security> security(AuditQuery query) {
        validate(query);

        return PageData.of(
                mapper.selectSecurityList(query),
                query.page().page(),
                query.page().size(),
                mapper.selectSecurityCount(query));
    }

    /** 환경 상한은 기본 31일보다 더 좁게 설정할 수 있다. */
    private void validate(AuditQuery query) {
        if (Duration.between(query.from(), query.to()).compareTo(Duration.ofDays(maxDays)) > 0) {
            throw new ApiException(ApiError.VALIDATION_FAILED);
        }
    }
}
