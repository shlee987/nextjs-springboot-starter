package com.example.starter.common.persistence;

import com.example.starter.common.config.AppProperties;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.Deque;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 실제 parameterize의 BoundSql과 안전한 파라미터를 local에서만 표시한다. */
@Component
@Profile("local")
@ConditionalOnProperty(name = "app.logging.sql.enabled", havingValue = "true")
@Intercepts({
    @Signature(
            type = StatementHandler.class,
            method = "parameterize",
            args = {Statement.class}),
    @Signature(
            type = Executor.class,
            method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(
            type = Executor.class,
            method = "update",
            args = {MappedStatement.class, Object.class})
})
public class SqlDiagnostics implements Interceptor {
    private static final Logger LOG = LoggerFactory.getLogger("starter.sql");
    private final AppProperties.Sql policy;
    private final ThreadLocal<Deque<Call>> calls = ThreadLocal.withInitial(ArrayDeque::new);

    /** local 상세 여부와 공통 slow 요약을 별도로 적용한다. */
    public SqlDiagnostics(AppProperties properties) {
        this.policy = properties.logging().sql();
    }

    /** query overload는 한 번만 가로채며 순수 DB 시간이 아닌 mapperElapsedMs를 측정한다. */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (invocation.getTarget() instanceof StatementHandler handler) {
            var stack = calls.get();
            if (!stack.isEmpty() && policy.enabled() && LOG.isDebugEnabled()) {
                Call call = stack.peek();
                try {
                    var bound = handler.getBoundSql();
                    call.detail =
                            SqlParameterDisplay.sensitive(call.statement.getId(), bound.getSql())
                                    ? "  [AUTH_SQL_OMITTED]"
                                    : SqlParameterDisplay.format(
                                            call.statement.getConfiguration(),
                                            bound,
                                            policy.maxChars());
                } catch (RuntimeException unavailable) {
                    call.detail = "  [DETAIL_UNAVAILABLE]";
                }
            }
            if (stack.isEmpty()) {
                calls.remove();
            }
            return invocation.proceed();
        }
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        Call call = new Call(statement);
        calls.get().push(call);
        long started = System.nanoTime();
        boolean success = false;
        try {
            Object result = invocation.proceed();
            success = true;
            return result;
        } finally {
            calls.get().pop();
            if (calls.get().isEmpty()) {
                calls.remove();
            }
            long elapsed = (System.nanoTime() - started) / 1_000_000;
            if (elapsed >= policy.slowThreshold().toMillis()) {
                LOG.warn(
                        "mapper_slow statementId={} mapperElapsedMs={} result={}",
                        statement.getId(),
                        elapsed,
                        success ? "SUCCESS" : "FAILURE");
            }
            if (policy.enabled() && LOG.isDebugEnabled()) {
                String id = statement.getId();
                int method = id.lastIndexOf('.');
                String shortId =
                        method < 0 ? id : id.substring(id.lastIndexOf('.', method - 1) + 1);
                LOG.debug(
                        "{} ({}ms, {})\n{}",
                        shortId,
                        elapsed,
                        success ? "SUCCESS" : "FAILURE",
                        call.detail);
            }
        }
    }

    /** 실행 호출마다 보관하고 finally에서 제거하는 짧은 진단 문맥이다. */
    private static final class Call {
        final MappedStatement statement;
        String detail = "  [BINDING_NOT_REACHED]";

        /** cache hit/prepare 실패에서는 실행되지 않은 파라미터를 추측해 표시하지 않는다. */
        Call(MappedStatement statement) {
            this.statement = statement;
        }
    }
}
