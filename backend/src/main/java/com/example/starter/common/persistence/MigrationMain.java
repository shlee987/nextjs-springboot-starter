package com.example.starter.common.persistence;

import com.example.starter.common.config.EnvironmentGuard;
import java.util.Set;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 공유 환경 migration 전용 진입점이다. 웹·인증·키 초기화를 기동하지 않는다. */
public final class MigrationMain {
    /** 전용 CLI 타입의 인스턴스 생성을 막는다. */
    private MigrationMain() {}

    /** 운영자가 공급한 migration 계정으로만 DDL을 적용한다. 실제 실행은 별도 승인 대상이다. */
    public static void main(String[] args) {
        String profile = required("SPRING_PROFILES_ACTIVE");
        String url = required("DB_URL");

        if (!Set.of("local", "dev", "stg", "prod").contains(profile)
                || !url.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException(
                    "Explicit profile and verified PostgreSQL target required");
        }

        var database = EnvironmentGuard.validateDatabaseUrl(url, "local".equals(profile));

        if ("local".equals(profile) && !EnvironmentGuard.isLoopback(database.getHost())) {
            throw new IllegalStateException("Local migration requires loopback database");
        }

        String store = System.getenv().getOrDefault("APP_AUTH_STATE_STORE", "redis");

        if (!Set.of("redis", "jdbc").contains(store)) {
            throw new IllegalStateException("Invalid state store");
        }

        var source =
                new DriverManagerDataSource(url, required("DB_USERNAME"), required("DB_PASSWORD"));
        FoundationMigrations.migrate(
                source,
                "jdbc".equals(store),
                Boolean.parseBoolean(System.getenv("APP_AUTH_PRELOGIN_SESSION_ENABLED")),
                "local".equals(profile));
    }

    /** 비밀을 명령 인자나 오류 문구에 포함하지 않는다. */
    private static String required(String key) {
        String value = System.getenv(key);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + ": external value required");
        }

        return value;
    }
}
