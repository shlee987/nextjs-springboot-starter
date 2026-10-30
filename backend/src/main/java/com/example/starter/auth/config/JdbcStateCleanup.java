package com.example.starter.auth.config;

import com.example.starter.auth.store.AuthStateMapper;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** JDBC 인증 만료 정리만 제공하며 감사 이력은 삭제하지 않는다. */
@Configuration(proxyBeanMethods = false)
@DependsOn("foundationMigrations")
@ConditionalOnProperty(name = "app.auth.state-store", havingValue = "jdbc")
@EnableScheduling
public class JdbcStateCleanup {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcStateCleanup.class);
    private final AuthStateMapper mapper;
    private final Clock clock;
    private final TransactionTemplate transaction;

    /** 짧은 transaction과 최대1000건 batch를 사용한다. */
    public JdbcStateCleanup(
            AuthStateMapper mapper, Clock clock, PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.clock = clock;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setTimeout(3);
    }

    /** 절대 만료가 지난 tombstone만 SKIP LOCKED로 지운다. */
    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void cleanExpired() {
        try {
            transaction.executeWithoutResult(
                    status -> {
                        mapper.deleteExpiredLogins(clock.millis());
                        mapper.deleteExpiredBuckets(clock.millis());
                    });
        } catch (RuntimeException failure) {
            LOG.error("auth_cleanup_failed category={}", failure.getClass().getSimpleName());
        }
    }
}
