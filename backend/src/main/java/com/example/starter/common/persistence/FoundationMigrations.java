package com.example.starter.common.persistence;

import com.example.starter.common.config.AppProperties;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Boot 자동 runner와 중복하지 않고 독립 history를 정해진 순서로 적용한다. */
@Configuration(value = "foundationMigrationConfiguration", proxyBeanMethods = false)
public class FoundationMigrations {
    /** 승인된 local 앱 기동은 core→선택 auth→선택 session→local을 적용한다. */
    @Bean
    InitializingBean foundationMigrations(
            DataSource source, AppProperties properties, Environment environment) {
        return () -> {
            if (environment.getProperty("spring.flyway.enabled", Boolean.class, false)) {
                if (!properties.bootstrap().localDataEnabled()) {
                    throw new IllegalStateException("Automatic migrations are local-only");
                }

                migrate(
                        source,
                        properties.auth().stateStore() == AppProperties.StateStore.JDBC,
                        properties.auth().preloginSession().enabled(),
                        true);
            }
        };
    }

    /** runner 전체를 DB transaction advisory lock으로 직렬화하고 독립 history 순서로 적용한다. */
    public static void migrate(DataSource source, boolean jdbc, boolean prelogin, boolean local) {
        try (Connection lock = source.getConnection()) {
            lock.setAutoCommit(false);
            try {
                try (var statement =
                        lock.prepareStatement("SELECT pg_advisory_xact_lock(738492615029)")) {
                    statement.execute();
                }
                run(source, lock, "core", "public", "flyway_schema_history");
                if (jdbc) {
                    run(source, lock, "auth-jdbc", "public", "auth_jdbc_schema_history");
                    if (prelogin) {
                        run(source, lock, "session-jdbc", "auth_session", "flyway_schema_history");
                    }
                }
                if (local) {
                    run(source, lock, "local", "public", "local_seed_schema_history");
                }
            } finally {
                lock.rollback();
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Foundation migration initialization failed", failure);
        }
    }

    /** 기존 이력은 그대로 migrate하고 검증된 신규 독립 이력만 명시적으로 초기화한다. */
    private static void run(
            DataSource source,
            Connection connection,
            String directory,
            String schema,
            String history)
            throws SQLException {
        Flyway flyway = configured(source, directory, schema, history).load();
        if ("public".equals(schema)
                && !"core".equals(directory)
                && !IndependentHistoryInitializer.exists(connection, history)) {
            IndependentHistoryInitializer.verify(source, connection, directory);
            flyway.baseline();
        }
        flyway.migrate();
    }

    /** baseline 자동 수용은 금지하며 초기화 버전0은 모든 V1 이상을 pending으로 유지한다. */
    static FluentConfiguration configured(
            DataSource source, String directory, String schema, String history) {
        return Flyway.configure()
                .dataSource(source)
                .defaultSchema(schema)
                .schemas(schema)
                .table(history)
                .locations("classpath:db/migration/" + directory)
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .baselineVersion("0")
                .baselineDescription("Foundation independent history initialization")
                .validateMigrationNaming(true);
    }
}
