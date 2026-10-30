package com.example.starter.common.persistence;

import com.example.starter.common.config.AppProperties;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 상세 SQL interceptor가 없는 공유 모드에서 안전한 slow 요약만 측정한다. */
@Component
@ConditionalOnProperty(
        name = "app.logging.sql.enabled",
        havingValue = "false",
        matchIfMissing = true)
@Intercepts({
    @Signature(
            type = Executor.class,
            method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(
            type = Executor.class,
            method = "update",
            args = {MappedStatement.class, Object.class})
})
public class SlowMapperDiagnostics implements Interceptor {
    private static final Logger LOG = LoggerFactory.getLogger(SlowMapperDiagnostics.class);
    private final long thresholdMillis;

    /** mapper 순수 호출 시간 상한만 읽고 BoundSql에는 접근하지 않는다. */
    public SlowMapperDiagnostics(AppProperties properties) {
        this.thresholdMillis = properties.logging().sql().slowThreshold().toMillis();
    }

    /** statement ID·걸린 시간·고정 결과 외 parameter/result/SQL/cause는 출력하지 않는다. */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long started = System.nanoTime();
        boolean success = false;

        try {
            Object result = invocation.proceed();
            success = true;

            return result;
        } finally {
            long elapsed = (System.nanoTime() - started) / 1_000_000;

            if (elapsed >= thresholdMillis) {
                String statementId = ((MappedStatement) invocation.getArgs()[0]).getId();
                LOG.warn(
                        "mapper_slow statementId={} mapperElapsedMs={} result={}",
                        statementId,
                        elapsed,
                        success ? "SUCCESS" : "FAILURE");
            }
        }
    }
}
