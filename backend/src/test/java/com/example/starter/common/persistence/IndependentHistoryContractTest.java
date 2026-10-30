package com.example.starter.common.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.TestPolicies;
import com.example.starter.common.config.AppProperties;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.api.CoreMigrationType;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.internal.info.MigrationInfoContext;
import org.flywaydb.core.internal.resolver.ResolvedMigrationImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;

/** 실제 Flyway12 상태 판정·제품 설정과 catalog 실패 경계를 DB 없이 검사한다. */
class IndependentHistoryContractTest {
    /** 실제 component 이름과 초기화 bean 이름은 충돌하지 않고 disabled runner는 연결하지 않는다. */
    @Test
    void configurationAndInitializerRegisterWithoutOverridingBeans() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setAllowBeanDefinitionOverriding(false);
            context.setEnvironment(
                    new MockEnvironment().withProperty("spring.flyway.enabled", "false"));
            context.registerBean(DataSource.class, () -> new DriverManagerDataSource());
            context.registerBean(AppProperties.class, TestPolicies::local);
            context.register(FoundationMigrations.class);
            context.refresh();
            assertTrue(context.getBean("foundationMigrations") instanceof InitializingBean);
            assertTrue(
                    context.getBean("foundationMigrationConfiguration")
                            instanceof FoundationMigrations);
        }
    }

    /** 독립 경로/이력·baseline0·자동 baseline 금지이며 V1/localV2가 pending으로 남는다. */
    @Test
    void installedFlywayKeepsEveryInitialMigrationPendingAboveExplicitZeroBaseline()
            throws Exception {
        for (String[] runner :
                new String[][] {
                    {"core", "public", "flyway_schema_history", "2"},
                    {"auth-jdbc", "public", "auth_jdbc_schema_history", "1"},
                    {"session-jdbc", "auth_session", "flyway_schema_history", "1"},
                    {"local", "public", "local_seed_schema_history", "2"}
                }) {
            var config =
                    FoundationMigrations.configured(
                            new DriverManagerDataSource(), runner[0], runner[1], runner[2]);
            assertFalse(config.isBaselineOnMigrate());
            assertTrue(config.isCleanDisabled());
            assertEquals("0", config.getBaselineVersion().toString());
            assertEquals(runner[1], config.getDefaultSchema());
            assertEquals(runner[2], config.getTable());
            assertEquals(
                    "classpath:db/migration/" + runner[0],
                    config.getLocations()[0].getDescriptor());
            var resources =
                    new PathMatchingResourcePatternResolver()
                            .getResources("classpath*:db/migration/" + runner[0] + "/*.sql");
            assertEquals(Integer.parseInt(runner[3]), resources.length);
            for (var resource : resources) {
                String version = resource.getFilename().substring(1, 2);
                var context = new MigrationInfoContext();
                context.appliedBaseline = config.getBaselineVersion();
                context.lastApplied = config.getBaselineVersion();
                context.target = MigrationVersion.LATEST;
                var migration =
                        new ResolvedMigrationImpl(
                                MigrationVersion.fromVersion(version),
                                "synthetic",
                                resource.getFilename(),
                                1,
                                null,
                                CoreMigrationType.SQL,
                                "classpath",
                                null);
                assertEquals(MigrationState.PENDING, migration.getState(context));
                context.appliedBaseline = MigrationVersion.fromVersion("1");
                context.lastApplied = context.appliedBaseline;
                if (version.equals("1")) {
                    assertEquals(MigrationState.BASELINE_IGNORED, migration.getState(context));
                }
            }
        }
    }

    /** 이미 검증된 manifest 외 relation·소유 이름 충돌·누락·routine/type는 fail closed다. */
    @Test
    void catalogAcceptsOnlyCompleteOwnedObjectsAndRejectsCollisionsOrUnknownObjects()
            throws Exception {
        Map<String, String> core = IndependentHistoryInitializer.relations("core");
        assertEquals("r", core.get("APP_USER"));
        assertEquals("S", core.get("SEQ_APP_USER_USER_ID"));
        assertEquals("i", core.get("PK_APP_USER"));
        assertEquals("i", core.get("UX_USER_DEPARTMENT_PRIMARY"));
        assertEquals(13, core.values().stream().filter("r"::equals).count());
        assertTrue(IndependentHistoryInitializer.relations("local").isEmpty());
        IndependentHistoryInitializer.verifyCatalog(catalog(core, false), core);
        var unknown = new HashMap<>(core);
        unknown.put("UNKNOWN_TABLE", "r");
        assertThrows(
                IllegalStateException.class,
                () -> IndependentHistoryInitializer.verifyCatalog(catalog(unknown, false), core));
        var collision = new HashMap<>(core);
        collision.putAll(IndependentHistoryInitializer.relations("auth-jdbc"));
        assertThrows(
                IllegalStateException.class,
                () -> IndependentHistoryInitializer.verifyCatalog(catalog(collision, false), core));
        var missing = new HashMap<>(core);
        missing.remove("APP_USER");
        assertThrows(
                IllegalStateException.class,
                () -> IndependentHistoryInitializer.verifyCatalog(catalog(missing, false), core));
        assertThrows(
                IllegalStateException.class,
                () -> IndependentHistoryInitializer.verifyCatalog(catalog(core, true), core));
    }

    /** pg_catalog 응답만 합성하며 Flyway/제품 앱을 실제 데이터베이스에 연결하지 않는다. */
    private Connection catalog(Map<String, String> relations, boolean unknownType) {
        var queries = new AtomicInteger();
        var rows = new ArrayList<>(relations.entrySet());
        return proxy(
                Connection.class,
                (method, args) ->
                        switch (method) {
                            case "prepareStatement" -> {
                                boolean catalog = queries.getAndIncrement() == 0;
                                var cursor = new AtomicInteger(-1);
                                yield proxy(
                                        PreparedStatement.class,
                                        (name, values) ->
                                                switch (name) {
                                                    case "executeQuery" ->
                                                            proxy(
                                                                    ResultSet.class,
                                                                    (operation, fields) ->
                                                                            switch (operation) {
                                                                                case "next" ->
                                                                                        cursor
                                                                                                        .incrementAndGet()
                                                                                                < (catalog
                                                                                                        ? rows
                                                                                                                .size()
                                                                                                        : 1);
                                                                                case "getString" ->
                                                                                        ((Integer)
                                                                                                                fields[
                                                                                                                        0])
                                                                                                        == 1
                                                                                                ? rows.get(
                                                                                                                cursor
                                                                                                                        .get())
                                                                                                        .getKey()
                                                                                                : rows.get(
                                                                                                                cursor
                                                                                                                        .get())
                                                                                                        .getValue();
                                                                                case "getBoolean" ->
                                                                                        unknownType;
                                                                                case "close" ->
                                                                                        null;
                                                                                default ->
                                                                                        throw new AssertionError(
                                                                                                operation);
                                                                            });
                                                    case "close" -> null;
                                                    default -> throw new AssertionError(name);
                                                });
                            }
                            default -> throw new AssertionError(method);
                        });
    }

    /** 불필요한 SQL 호출은 즉시 시험을 실패시킨다. */
    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Invocation invocation) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (target, method, args) -> invocation.call(method.getName(), args));
    }

    /** JDBC 호출 경계의 합성 응답이다. */
    private interface Invocation {
        Object call(String name, Object[] arguments);
    }
}
