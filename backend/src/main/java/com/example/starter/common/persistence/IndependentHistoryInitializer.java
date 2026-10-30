package com.example.starter.common.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.flywaydb.core.api.MigrationState;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** core가 만든 public에만 신규 독립 history0을 허용하고 unknown/소유 객체 충돌은 거부한다. */
final class IndependentHistoryInitializer {
    /** migration runner만 사용하는 초기화 도구다. */
    private IndependentHistoryInitializer() {}

    /** relation 종류와 무관하게 이름이 이미 있으면 기존 이력으로 취급하고 덮어쓰지 않는다. */
    static boolean exists(Connection connection, String name) throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        "SELECT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid ="
                            + " c.relnamespace WHERE n.nspname = 'public' AND c.relname = ?)")) {
            statement.setString(1, name);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    /** 기존 모든 foundation 이력을 검증하고 그 SQL이 선언한 relation만 허용한다. */
    static void verify(DataSource source, Connection connection, String directory)
            throws SQLException {
        Map<String, String> allowed = new HashMap<>();
        for (String[] runner :
                new String[][] {
                    {"core", "flyway_schema_history"},
                    {"auth-jdbc", "auth_jdbc_schema_history"},
                    {"local", "local_seed_schema_history"}
                }) {
            boolean present = exists(connection, runner[1]);
            if (!present && "core".equals(runner[0])) {
                throw new IllegalStateException("Verified core history required");
            }
            if (present) {
                var flyway =
                        FoundationMigrations.configured(source, runner[0], "public", runner[1])
                                .load();
                flyway.validate();
                var infos = flyway.info().all();
                long applied = 0;
                for (var info : infos) {
                    if (info.getState() == MigrationState.SUCCESS) {
                        applied++;
                    } else if (info.getState() != MigrationState.BASELINE
                            || !"0".equals(info.getVersion().toString())) {
                        throw new IllegalStateException(
                                "Complete verified foundation history required");
                    }
                }
                if (applied == 0) {
                    throw new IllegalStateException("Applied foundation migration required");
                }
                allowed.putAll(relations(runner[0]));
                allowed.put(runner[1], "r");
                allowed.put(runner[1] + "_pk", "i");
                allowed.put(runner[1] + "_s_idx", "i");
            }
        }
        verifyCatalog(connection, allowed);
        if ("local".equals(directory)) {
            try (var statement =
                            connection.prepareStatement(
                                    "SELECT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname ="
                                            + " 'starter_local_only')");
                    var result = statement.executeQuery()) {
                result.next();
                if (result.getBoolean(1)) {
                    throw new IllegalStateException("Local migration owned schema already exists");
                }
            }
        }
    }

    /** 공개 schema의 relation·독립 type·routine은 승인된 SQL manifest와 일치해야 한다. */
    static void verifyCatalog(Connection connection, Map<String, String> allowed)
            throws SQLException {
        try (var statement =
                        connection.prepareStatement(
                                "SELECT c.relname, c.relkind::text FROM pg_class c JOIN"
                                    + " pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname ="
                                    + " 'public'");
                var result = statement.executeQuery()) {
            Map<String, String> actual = new HashMap<>();
            while (result.next()) {
                actual.put(result.getString(1), result.getString(2));
            }
            if (!actual.equals(allowed)) {
                throw new IllegalStateException("Unknown or conflicting foundation objects");
            }
        }
        try (var statement =
                        connection.prepareStatement(
                                """
                                SELECT EXISTS (
                                    SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
                                        WHERE n.nspname = 'public'
                                    UNION ALL
                                    SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace
                                        WHERE n.nspname = 'public' AND t.typrelid = 0 AND t.typelem = 0
                                )
                                """);
                var result = statement.executeQuery()) {
            result.next();
            if (result.getBoolean(1)) {
                throw new IllegalStateException("Unknown foundation routines or types");
            }
        }
    }

    /** 현재 migration SQL의 명시적 public table·index·identity 이름만 manifest로 읽는다. */
    static Map<String, String> relations(String directory) {
        Map<String, String> names = new HashMap<>();
        if ("local".equals(directory)) {
            return names;
        }
        try {
            var resources =
                    new PathMatchingResourcePatternResolver()
                            .getResources("classpath*:db/migration/" + directory + "/*.sql");
            for (var resource : resources) {
                String sql = resource.getContentAsString(StandardCharsets.UTF_8);
                add(names, sql, "CREATE TABLE \"([^\"]+)\"", "r");
                add(names, sql, "CREATE (?:UNIQUE )?INDEX \"([^\"]+)\"", "i");
                add(names, sql, "SEQUENCE NAME \"([^\"]+)\"", "S");
                add(names, sql, "CONSTRAINT \"([^\"]+)\"\\s+(?:PRIMARY KEY|UNIQUE)", "i");
            }
            return names;
        } catch (IOException failure) {
            throw new IllegalStateException("Foundation SQL manifest unavailable", failure);
        }
    }

    /** quoted identifier만 읽으며 SQL 실행이나 사용자 입력 결합은 하지 않는다. */
    private static void add(Map<String, String> names, String sql, String expression, String kind) {
        var matcher = Pattern.compile(expression).matcher(sql);
        while (matcher.find()) {
            names.put(matcher.group(1), kind);
        }
    }
}
