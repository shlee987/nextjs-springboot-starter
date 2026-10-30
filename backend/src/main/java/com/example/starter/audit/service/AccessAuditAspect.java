package com.example.starter.audit.service;

import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 변경은 transaction500 안에서 callback을 등록해 실제 완료 상태로만 기록한다. */
@Aspect
@Component
@Order(1000)
public class AccessAuditAspect {
    private static final Logger LOG = LoggerFactory.getLogger(AccessAuditAspect.class);
    private final AuditWriter writer;

    /** 범용 변경 advice는 BEST_EFFORT 전용이고 필수 변경은 별도 업무 workflow가 소유한다. */
    public AccessAuditAspect(AuditWriter writer) {
        this.writer = writer;
    }

    /** 메서드 반환을 성공 commit으로 간주하지 않으며 callback 불명확은 UNKNOWN이다. */
    @Around("@annotation(audit)")
    public Object record(ProceedingJoinPoint invocation, AccessAudit audit) throws Throwable {
        if (audit.action() == AccessAudit.Action.READ
                || audit.action() == AccessAudit.Action.SEARCH) {
            return invocation.proceed();
        }

        if (audit.policy() == AccessAudit.Policy.REQUIRED) {
            throw new IllegalStateException(
                    "Required mutations must use the transactional audit workflow");
        }

        String operation = UUID.randomUUID().toString();
        boolean transactional = TransactionSynchronizationManager.isSynchronizationActive();

        if (transactional) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        /** 업무 rollback/commit/미확정과 같은 의미로 append 이벤트를 기록한다. */
                        @Override
                        public void afterCompletion(int status) {
                            String outcome =
                                    status == STATUS_COMMITTED
                                            ? "SUCCESS"
                                            : status == STATUS_ROLLED_BACK ? "FAILURE" : "UNKNOWN";
                            bestEffort(audit, outcome, operation);
                        }
                    });
        }

        try {
            Object result = invocation.proceed();

            if (!transactional) {
                bestEffort(audit, "UNKNOWN", operation);
            }

            return result;
        } catch (Throwable failure) {
            if (!transactional) {
                bestEffort(audit, "UNKNOWN", operation);
            }

            throw failure;
        }
    }

    /** 실제 업무 결과를 감사 실패로 덮거나 raw exception을 출력하지 않는다. */
    private void bestEffort(AccessAudit audit, String outcome, String operation) {
        try {
            writer.access(
                    audit.resource().name(), audit.action().name(), null, outcome, null, operation);
        } catch (RuntimeException failure) {
            LOG.error(
                    "audit_write_failed operationId={} resource={} outcome={}",
                    operation,
                    audit.resource(),
                    outcome);
        }
    }
}
