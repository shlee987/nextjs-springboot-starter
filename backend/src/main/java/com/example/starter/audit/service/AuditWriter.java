package com.example.starter.audit.service;

import com.example.starter.audit.mapper.AuditMapper;
import com.example.starter.audit.model.AuditError;
import com.example.starter.audit.model.AuditEvent;
import com.example.starter.auth.model.AuthContext;
import com.example.starter.auth.model.AuthLogin;
import com.example.starter.common.config.AppProperties;
import com.example.starter.common.web.ApiException;
import com.example.starter.common.web.RequestContext;
import com.example.starter.common.web.RequestTrace;
import com.example.starter.common.web.UtcTimes;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;

/** low-level writer는 감사 advice 대상이 아니며 읽기 이력은 독립 commit한다. */
@Service
public class AuditWriter {
    private final AuditMapper mapper;
    private final Clock clock;
    private final String serverId;
    private final TransactionTemplate isolated;
    private final LoginReferences references;

    /** 공통 Clock과 서버 식별자로 안전한 이벤트를 만든다. */
    public AuditWriter(
            AuditMapper mapper,
            Clock clock,
            AppProperties properties,
            PlatformTransactionManager manager,
            LoginReferences references) {
        this.mapper = mapper;
        this.references = references;
        this.clock = clock;
        this.serverId = properties.serverId();
        this.isolated = new TransactionTemplate(manager);
        this.isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.isolated.setTimeout(3);
    }

    /** 업무 rollback과 분리한 접근 이벤트를 짧은 별도 연결에 append한다. */
    public void access(
            String resource,
            String action,
            String target,
            String outcome,
            String reason,
            String operation) {
        try {
            isolated.executeWithoutResult(
                    status ->
                            mapper.insertAccess(
                                    event(
                                            resource, action, target, outcome, reason, operation,
                                            null, null, null)));
        } catch (RuntimeException exception) {
            throw new ApiException(AuditError.AUDIT_UNAVAILABLE, exception);
        }
    }

    /** 토큰 발급 전·폐기 후 보안 이력을 독립 commit한다. */
    public void security(String code, Long targetUserId, String outcome, String reason) {
        try {
            isolated.executeWithoutResult(
                    status -> insertSecurity(code, targetUserId, outcome, reason));
        } catch (RuntimeException exception) {
            throw new ApiException(AuditError.AUDIT_UNAVAILABLE, exception);
        }
    }

    /** 새로 발급/폐기한 sid는 원문 대신 전용 HMAC 지문으로 기록한다. */
    public void security(
            String code, Long targetUserId, String outcome, String reason, String sid) {
        try {
            isolated.executeWithoutResult(
                    status ->
                            mapper.insertSecurity(
                                    event(
                                            null,
                                            null,
                                            null,
                                            outcome,
                                            reason,
                                            null,
                                            code,
                                            targetUserId,
                                            sid)));
        } catch (RuntimeException exception) {
            throw new ApiException(AuditError.AUDIT_UNAVAILABLE, exception);
        }
    }

    /** 인증된 업무의 sid만 선택하고 미검증 쿠키나 URL 입력은 사용하지 않는다. */
    private String currentSid() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        return authentication != null && authentication.getPrincipal() instanceof AuthLogin login
                ? login.sid()
                : null;
    }

    /** 필수 계정·권한 변경 성공은 반드시 업무 DB transaction에 참여한다. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void securityInTransaction(String code, Long targetUserId) {
        insertSecurity(code, targetUserId, "SUCCESS", null);
    }

    /** 필수 변경의 접근 성공 이벤트도 업무 commit과 함께 저장한다. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void accessInTransaction(String resource, String operation) {
        accessInTransaction(resource, "UPDATE", operation);
    }

    /** 파일 생성/삭제도 고정 action과 업무 commit을 함께 기록한다. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void accessInTransaction(String resource, String action, String operation) {
        try {
            mapper.insertAccess(
                    event(resource, action, null, "SUCCESS", null, operation, null, null, null));
        } catch (RuntimeException failure) {
            throw new ApiException(AuditError.AUDIT_UNAVAILABLE, failure);
        }
    }

    /** 내부 식별자·고정 코드와 최외곽 요청 문맥의 직접 peer만 영속한다. */
    private AuditEvent event(
            String resource,
            String action,
            String target,
            String outcome,
            String reason,
            String operation,
            String code,
            Long targetUserId,
            String sid) {
        Long actor = AuthContext.actor();
        String verifiedActor = MDC.get("actorUserId");

        if (actor == null && verifiedActor != null && verifiedActor.matches("[1-9][0-9]{0,18}")) {
            actor = Long.valueOf(verifiedActor);
        }

        if (actor == null && ("LOGIN_SUCCESS".equals(code) || "TOKEN_REFRESHED".equals(code))) {
            actor = targetUserId;
        }
        boolean request =
                RequestContextHolder.getRequestAttributes() != null || MDC.get("traceId") != null;

        return new AuditEvent(
                UUID.randomUUID().toString(),
                UtcTimes.millis(clock.instant()),
                actor,
                actor == null ? request ? "ANONYMOUS" : "SYSTEM" : "USER",
                resource,
                action,
                target == null ? null : "USER",
                target,
                outcome,
                reason,
                RequestTrace.current(),
                serverId,
                operation,
                code,
                targetUserId,
                references.forSid(sid != null ? sid : currentSid()),
                RequestContext.currentPeer());
    }

    /** 이력 쓰기 실패는 일반 인증 성공으로 바꾸지 않는다. */
    private void insertSecurity(String code, Long targetUserId, String outcome, String reason) {
        try {
            mapper.insertSecurity(
                    event(null, null, null, outcome, reason, null, code, targetUserId, null));
        } catch (RuntimeException exception) {
            throw new ApiException(AuditError.AUDIT_UNAVAILABLE, exception);
        }
    }
}
