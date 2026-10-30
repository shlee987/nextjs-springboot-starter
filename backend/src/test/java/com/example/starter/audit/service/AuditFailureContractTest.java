package com.example.starter.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.starter.TestPolicies;
import com.example.starter.TestTransactions;
import com.example.starter.audit.mapper.AuditMapper;
import com.example.starter.audit.model.AuditError;
import com.example.starter.audit.model.AuditEvent;
import com.example.starter.common.web.ApiException;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

/** 실제 advice/writer를 실행하되 DB 대신 INSERT 실패와 transaction callback만 제어한다. */
class AuditFailureContractTest {
    /** STARTED 실패에는 민감 업무가 실행되지 않고 종료 이력 실패에는 결과가 차단된다. */
    @Test
    void requiredReadsFailClosedBeforeReturningData() {
        for (int failAt : new int[] {1, 2}) {
            List<AuditEvent> events = new ArrayList<>();
            var transactions = new TestTransactions();
            var service = new SensitiveService();
            var proxy = proxy(service, writer(events, transactions, failAt));
            var failure = assertThrows(ApiException.class, proxy::read);
            assertEquals(AuditError.AUDIT_UNAVAILABLE, failure.error());
            assertEquals(failAt == 2, service.called);
            assertEquals(3, transactions.timeout);
            assertEquals(1, transactions.rollbacks);
        }
    }

    /** 필수 조회의 두 append 이벤트가 같은 operation으로 연결된다. */
    @Test
    void requiredReadsAppendStartedAndTerminalEvents() {
        List<AuditEvent> events = new ArrayList<>();
        var transactions = new TestTransactions();
        var proxy = proxy(new SensitiveService(), writer(events, transactions, 0));
        assertEquals("sensitive-result", proxy.read());
        assertEquals(
                List.of("STARTED", "SUCCESS"), events.stream().map(AuditEvent::outcome).toList());
        assertEquals(events.getFirst().operationId(), events.getLast().operationId());
        assertEquals("SYSTEM", events.getFirst().actorKind());
        assertEquals(2, transactions.commits);
    }

    /** BEST_EFFORT는 감사 INSERT 오류로 이미 결정한 결과를 바꾸지 않는다. */
    @Test
    void bestEffortKeepsBusinessResult() {
        var proxy =
                proxy(new SensitiveService(), writer(new ArrayList<>(), new TestTransactions(), 1));
        assertEquals("ordinary-result", proxy.ordinary());
    }

    /** commit 오류도 원문 실패 대신 AUDIT_UNAVAILABLE로 제한한다. */
    @Test
    void transactionCommitFailureBlocksRequiredRead() {
        var transactions = new TestTransactions();
        transactions.failCommit = true;
        var service = new SensitiveService();
        var proxy = proxy(service, writer(new ArrayList<>(), transactions, 0));
        assertEquals(
                AuditError.AUDIT_UNAVAILABLE,
                assertThrows(ApiException.class, proxy::read).error());
        assertFalse(service.called);
    }

    /** 제품 writer가 관찰한 INSERT만 수집하고 SQL은 실행하지 않는다. */
    private AuditWriter writer(List<AuditEvent> events, TestTransactions transactions, int failAt) {
        int[] calls = {0};
        AuditMapper mapper =
                (AuditMapper)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {AuditMapper.class},
                                (instance, method, arguments) -> {
                                    if (++calls[0] == failAt) {
                                        throw new IllegalStateException("SYNTHETIC_AUDIT_SECRET");
                                    }

                                    events.add((AuditEvent) arguments[0]);

                                    return 1;
                                });

        return new AuditWriter(
                mapper,
                Clock.systemUTC(),
                TestPolicies.local(),
                transactions,
                TestPolicies.references());
    }

    /** 실제 Spring AOP로 advice를 적용해 자기 호출 우회 시험을 피한다. */
    private SensitiveService proxy(SensitiveService target, AuditWriter writer) {
        var factory = new AspectJProxyFactory(target);
        factory.addAspect(new ReadAccessAuditAspect(writer));
        factory.addAspect(new AccessAuditAspect(writer));

        return factory.getProxy();
    }

    /** 민감 결과 반환 전 감사 정책을 검사하는 격리 서비스다. */
    public static class SensitiveService {
        boolean called;

        /** 필수 저장이 확인되기 전에는 이 메서드가 실행되지 않아야 한다. */
        @AccessAudit(
                resource = AccessAudit.Resource.USER_LIST,
                action = AccessAudit.Action.SEARCH,
                policy = AccessAudit.Policy.REQUIRED)
        public String read() {
            called = true;

            return "sensitive-result";
        }

        /** 일반 결과는 이력 장애에도 그대로 보존한다. */
        @AccessAudit(resource = AccessAudit.Resource.DEPARTMENT, action = AccessAudit.Action.READ)
        public String ordinary() {
            return "ordinary-result";
        }
    }
}
