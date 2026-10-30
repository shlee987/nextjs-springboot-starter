package com.example.starter.audit.service;

import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** MethodSecurity200 뒤, 업무 transaction500 앞에서 조회 감사 연결을 닫고 진행한다. */
@Aspect
@Component
@Order(400)
public class ReadAccessAuditAspect {
    private static final Logger LOG = LoggerFactory.getLogger(ReadAccessAuditAspect.class);
    private final AuditWriter writer;

    /** 독립 감사 transaction은 업무 연결과 동시에 점유하지 않는다. */
    public ReadAccessAuditAspect(AuditWriter writer) {
        this.writer = writer;
    }

    /** 필수 조회는 STARTED 확인→업무 transaction 종료→종료 이력 확인 뒤 결과를 반환한다. */
    @Around("@annotation(audit)")
    public Object record(ProceedingJoinPoint invocation, AccessAudit audit) throws Throwable {
        if (audit.action() != AccessAudit.Action.READ
                && audit.action() != AccessAudit.Action.SEARCH) {
            return invocation.proceed();
        }

        String operation = UUID.randomUUID().toString();
        String target =
                audit.resource() == AccessAudit.Resource.USER_DETAIL
                        ? Long.toString((Long) invocation.getArgs()[0])
                        : null;

        if (audit.policy() == AccessAudit.Policy.REQUIRED) {
            writer.access(
                    audit.resource().name(),
                    audit.action().name(),
                    target,
                    "STARTED",
                    null,
                    operation);
        }

        Object result;

        try {
            result = invocation.proceed();
        } catch (Throwable failure) {
            terminal(audit, target, "FAILURE", "BUSINESS_FAILED", operation);

            throw failure;
        }

        terminal(audit, target, "SUCCESS", null, operation);

        return result;
    }

    /** BEST_EFFORT만 저장 오류를 정제 기록하고 원래 결과를 유지한다. */
    private void terminal(
            AccessAudit audit, String target, String outcome, String reason, String operation) {
        try {
            writer.access(
                    audit.resource().name(),
                    audit.action().name(),
                    target,
                    outcome,
                    reason,
                    operation);
        } catch (RuntimeException failure) {
            if (audit.policy() == AccessAudit.Policy.REQUIRED) {
                throw failure;
            }

            LOG.error(
                    "audit_write_failed operationId={} resource={} outcome={}",
                    operation,
                    audit.resource(),
                    outcome);
        }
    }
}
