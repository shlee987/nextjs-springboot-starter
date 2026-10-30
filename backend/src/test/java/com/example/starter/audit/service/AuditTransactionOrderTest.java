package com.example.starter.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.starter.TestPolicies;
import com.example.starter.TestTransactions;
import com.example.starter.audit.mapper.AuditMapper;
import com.example.starter.audit.model.AuditEvent;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

/** 실제 Spring advisor 순서에서 조회 audit/업무 transaction이 겹치지 않는지 검사한다. */
class AuditTransactionOrderTest {
    /** STARTED/업무/SUCCESS가 모두 별도 transaction 순서이며 최대 한 자원만 점유한다. */
    @Test
    void requiredReadDoesNotHoldBusinessConnectionDuringAudit() {
        try (var context = new AnnotationConfigApplicationContext(Wiring.class)) {
            assertEquals("result", context.getBean(Query.class).read());
            assertEquals(
                    List.of("STARTED", "BUSINESS", "SUCCESS"),
                    context.getBean(Events.class).values);
            assertEquals(3, context.getBean(TestTransactions.class).commits);
            assertEquals(0, context.getBean(TestTransactions.class).active);
        }
    }

    /** DB 대신 순서만 관찰하는 별도 시험 상태다. */
    static class Events {
        final List<String> values = new ArrayList<>();
    }

    /** 등록된 실제 두 aspect와 제품 transaction 순서를 그대로 적용한다. */
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableTransactionManagement(order = 500)
    @Import({ReadAccessAuditAspect.class, AccessAuditAspect.class})
    static class Wiring {
        /** 외부 연결이 없는 transaction 관찰자다. */
        @Bean
        TestTransactions transactions() {
            return new TestTransactions();
        }

        /** 현재 시험의 순서만 저장한다. */
        @Bean
        Events events() {
            return new Events();
        }

        /** 감사 INSERT 시 업무 transaction이 이미 끝났는지 확인한다. */
        @Bean
        AuditWriter writer(TestTransactions transactions, Events events) {
            var mapper =
                    (AuditMapper)
                            Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[] {AuditMapper.class},
                                    (proxy, method, args) -> {
                                        assertEquals(1, transactions.active);
                                        events.values.add(((AuditEvent) args[0]).outcome());

                                        return 1;
                                    });

            return new AuditWriter(
                    mapper,
                    Clock.systemUTC(),
                    TestPolicies.local(),
                    transactions,
                    TestPolicies.references());
        }

        /** transaction 적용 대상은 일반 Spring bean으로 등록한다. */
        @Bean
        Query query(TestTransactions transactions, Events events) {
            return new Query(transactions, events);
        }
    }

    /** 제품 조회와 같은 annotation·transaction 계약의 합성 업무다. */
    public static class Query {
        private final TestTransactions transactions;
        private final Events events;

        /** 실제 SQL 대신 자원 중첩 수를 관찰한다. */
        public Query(TestTransactions transactions, Events events) {
            this.transactions = transactions;
            this.events = events;
        }

        /** 조회 자체만 업무 transaction 안에 있고 이력은 바깥에서 저장해야 한다. */
        @Transactional(readOnly = true, rollbackFor = Exception.class)
        @AccessAudit(
                resource = AccessAudit.Resource.USER_LIST,
                action = AccessAudit.Action.SEARCH,
                policy = AccessAudit.Policy.REQUIRED)
        public String read() {
            assertEquals(1, transactions.active);
            events.values.add("BUSINESS");

            return "result";
        }
    }
}
