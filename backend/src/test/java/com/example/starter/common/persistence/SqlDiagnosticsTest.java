package com.example.starter.common.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.starter.TestPolicies;
import com.example.starter.common.web.ListQuery;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;

/** 안전한 표시와 실제 바인딩 객체/SQL/호출 횟수가 바뀌지 않는지 검사한다. */
class SqlDiagnosticsTest {
    /** 실제 XML의 foreach 추가 값과 DTO/Map 경로를 바인딩 순서대로 표시한다. */
    @Test
    void realForeachAndDtoParametersKeepJdbcOrderAndSensitiveFieldsMasked() throws Exception {
        var config = new Configuration();
        try (var input = new ClassPathResource("mappers/user/UserMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input, config, "user", config.getSqlFragments()).parse();
        }
        var foreach =
                config.getMappedStatement(
                                "com.example.starter.user.mapper.UserMapper.selectDepartmentList")
                        .getBoundSql(Map.of("userIds", List.of(9L, 20L)));
        String values = SqlParameterDisplay.format(config, foreach, 16000);
        assertTrue(values.contains("1. __frch_id_0"));
        assertTrue(values.contains("2. __frch_id_1"));
        assertTrue(values.contains("= 9"));
        assertTrue(values.contains("= 20"));
        var page =
                new ListQuery("SYNTHETIC_SEARCH\r\n[READY] forged", 2, 20, ListQuery.Sort.nameAsc);
        var bound =
                config.getMappedStatement(
                                "com.example.starter.user.mapper.UserMapper.selectUserList")
                        .getBoundSql(page);
        String output = SqlParameterDisplay.format(config, bound, 16000);
        assertFalse(output.contains("SYNTHETIC_SEARCH"));
        assertTrue(output.contains("[MASKED]"));
        assertTrue(
                output.contains("size (int/AUTO) = 20")
                        || output.contains("size (Object/AUTO) = 20"));
        assertTrue(output.contains("= 40"));
        assertEquals(20, page.size());
        assertEquals(40, page.offset());
    }

    /** null·추가값·단일값·악성 문자열·바이너리/스트림은 읽거나 임의 문자열화하지 않는다. */
    @Test
    void nullAndUnsafeObjectsAreBoundedWithoutConsumingOrStringifyingThem() {
        var config = new Configuration();
        var secret =
                new Object() {
                    public String toString() {
                        throw new AssertionError("Secret stringification");
                    }
                };
        InputStream stream =
                new InputStream() {
                    public int read() {
                        throw new AssertionError("Stream consumed");
                    }
                };
        var params = new HashMap<String, Object>();
        params.put("q", secret);
        params.put("password", secret);
        params.put("stream", stream);
        params.put("bytes", new byte[] {1, 2});
        params.put("userId", null);
        params.put("status", "ACTIVE\r\n[READY]\u001b[31m");
        params.put("from", Instant.parse("2026-10-29T00:00:00.123Z"));
        var names = List.of("q", "password", "stream", "bytes", "userId", "status", "from");
        var mappings =
                names.stream()
                        .map(
                                name ->
                                        new ParameterMapping.Builder(config, name, Object.class)
                                                .build())
                        .toList();
        var bound =
                new BoundSql(
                        config,
                        "SELECT '?' /* ? */ , $$?$$, data ? 'key' WHERE ID = ?",
                        mappings,
                        params);
        String sql = bound.getSql();
        String output = SqlParameterDisplay.format(config, bound, 16000);
        assertTrue(output.contains(sql));
        assertFalse(output.contains("[READY]"));
        assertFalse(output.contains("\u001b"));
        assertTrue(output.contains("userId (Object/AUTO) = null"));
        assertTrue(output.contains("2026-10-29 00:00:00.123"));
        assertEquals(sql, bound.getSql());
        assertSame(params, bound.getParameterObject());
        var scalar =
                new BoundSql(
                        config,
                        "SELECT ?",
                        List.of(new ParameterMapping.Builder(config, "userId", Long.class).build()),
                        17L);
        assertTrue(SqlParameterDisplay.format(config, scalar, 16000).contains("= 17"));
        var large = new BoundSql(config, "x".repeat(20000), mappings, params);
        assertTrue(SqlParameterDisplay.format(config, large, 512).length() < 550);
        assertTrue(
                SqlParameterDisplay.sensitive("com.example.starter.auth.Mapper.read", "SELECT ?"));
        assertTrue(
                SqlParameterDisplay.sensitive(
                        "UserMapper.read", "SELECT * FROM \"LOCAL_CREDENTIAL\""));
    }

    /** 많은 파라미터도 일부만 표시하고 OUT 값이나 위험한 속성명은 읽지 않는다. */
    @Test
    void parameterCountAndLabelsCannotExpandOrForgeTheLog() {
        var config = new Configuration();
        var many =
                IntStream.range(0, 100)
                        .mapToObj(
                                index ->
                                        new ParameterMapping.Builder(config, "offset", Long.class)
                                                .build())
                        .toList();
        String output =
                SqlParameterDisplay.format(
                        config,
                        new BoundSql(config, "SELECT ?", many, Map.of("offset", 3L)),
                        16000);
        assertTrue(output.contains("32. offset"));
        assertFalse(output.contains("33. offset"));
        assertTrue(output.contains("[PARAMS_TRUNCATED]"));
        var out =
                new ParameterMapping.Builder(config, "userId", Long.class)
                        .mode(ParameterMode.OUT)
                        .build();
        String text =
                SqlParameterDisplay.format(
                        config,
                        new BoundSql(config, "CALL example(?)", List.of(out), new DangerousDto()),
                        16000);
        assertTrue(text.contains("[OUT]"));
        var invalid =
                new ParameterMapping.Builder(config, "\r\n[READY] forged", String.class).build();
        text =
                SqlParameterDisplay.format(
                        config,
                        new BoundSql(config, "SELECT ?", List.of(invalid), Map.of()),
                        16000);
        assertTrue(text.contains("[PARAMETER]"));
        assertFalse(text.contains("[READY]"));
    }

    /** 진단이 DTO getter를 다시 실행하거나 미허용 필드를 읽지 않는다. */
    @Test
    void arbitraryDtoGettersAreNotInvoked() {
        var config = new Configuration();
        var mappings =
                List.of(
                        new ParameterMapping.Builder(config, "userId", Long.class).build(),
                        new ParameterMapping.Builder(config, "password", String.class).build());
        var bound = new BoundSql(config, "SELECT ?, ?", mappings, new DangerousDto());
        String text = SqlParameterDisplay.format(config, bound, 16000);
        assertTrue(text.contains("[UNAVAILABLE]"));
        assertTrue(text.contains("[MASKED]"));
    }

    /** shared 프로파일에는 상세 interceptor를 등록하지 않는다. */
    @Test
    void sharedDoesNotRegisterDetailedSqlEvenIfItsFlagIsForced() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var env = TestPolicies.environment();
            env.setActiveProfiles("prod");
            context.setEnvironment(env);
            context.register(SqlDiagnostics.class);
            context.refresh();
            assertTrue(context.getBeansOfType(SqlDiagnostics.class).isEmpty());
        }
    }

    /** 실제 실행에서 만들어진 BoundSql만 읽으며 SQL source/parameterize는 각각 한 번 실행된다. */
    @Test
    void interceptorUsesTheExecutedBoundSqlWithoutRebuildingOrChangingBinding() throws Exception {
        var configuration = new Configuration();
        var created = new AtomicInteger();
        var parameterized = new AtomicInteger();
        Object parameter = Map.of("userId", 21L, "q", "SYNTHETIC_SECRET");
        SqlSource source =
                actual -> {
                    created.incrementAndGet();
                    assertSame(parameter, actual);
                    return new BoundSql(
                            configuration,
                            "SELECT ? WHERE ? IS NOT NULL",
                            List.of(
                                    new ParameterMapping.Builder(
                                                    configuration, "userId", Long.class)
                                            .build(),
                                    new ParameterMapping.Builder(configuration, "q", String.class)
                                            .build()),
                            actual);
                };
        var mapped =
                new MappedStatement.Builder(
                                configuration,
                                "com.example.starter.user.mapper.UserMapper.example",
                                source,
                                SqlCommandType.SELECT)
                        .build();
        var diagnostics = new SqlDiagnostics(TestPolicies.local());
        var sqlLogger = (Logger) LoggerFactory.getLogger("starter.sql");
        Level previous = sqlLogger.getLevel();
        var captured = new ListAppender<ILoggingEvent>();
        captured.start();
        sqlLogger.setLevel(Level.DEBUG);
        sqlLogger.addAppender(captured);
        try {
            Executor executor =
                    (Executor)
                            Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[] {Executor.class},
                                    (target, method, args) -> {
                                        if (!method.getName().equals("query")) {
                                            throw new AssertionError(method.getName());
                                        }
                                        var bound = mapped.getBoundSql(args[1]);
                                        StatementHandler handler =
                                                (StatementHandler)
                                                        Proxy.newProxyInstance(
                                                                getClass().getClassLoader(),
                                                                new Class<?>[] {
                                                                    StatementHandler.class
                                                                },
                                                                (self, call, input) -> {
                                                                    if (call.getName()
                                                                            .equals(
                                                                                    "getBoundSql")) {
                                                                        return bound;
                                                                    }
                                                                    if (call.getName()
                                                                            .equals(
                                                                                    "parameterize")) {
                                                                        parameterized
                                                                                .incrementAndGet();
                                                                        assertSame(
                                                                                parameter,
                                                                                bound
                                                                                        .getParameterObject());
                                                                        return null;
                                                                    }
                                                                    throw new AssertionError(
                                                                            call.getName());
                                                                });
                                        ((StatementHandler) diagnostics.plugin(handler))
                                                .parameterize((Statement) null);
                                        return List.of();
                                    });
            ((Executor) diagnostics.plugin(executor))
                    .query(mapped, parameter, RowBounds.DEFAULT, null);
            assertEquals(1, created.get());
            assertEquals(1, parameterized.get());
            assertEquals(1, captured.list.size());
            String text = captured.list.getFirst().getFormattedMessage();
            assertTrue(text.contains("UserMapper.example"));
            assertTrue(text.contains("= 21"));
            assertTrue(text.contains("[MASKED]"));
            assertFalse(text.contains("SYNTHETIC_SECRET"));
        } finally {
            sqlLogger.detachAppender(captured);
            sqlLogger.setLevel(previous);
            captured.stop();
        }
    }

    /** 위험한 getter는 로깅이 접근하면 시험을 즉시 실패시킨다. */
    static class DangerousDto {
        /** 읽으면 값이 달라질 수 있는 임의 getter를 흉내 낸다. */
        public long getUserId() {
            throw new AssertionError("Getter invoked");
        }

        /** 민감한 getter는 절대 읽지 않는다. */
        public String getPassword() {
            throw new AssertionError("Password read");
        }
    }
}
