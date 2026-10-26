package com.example.starter.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;

/** 외부 연결 전 프로파일·자격·오류 비공개 정책의 최종 설정을 검증한다. */
public final class EnvironmentGuard implements EnvironmentPostProcessor, Ordered {
    private static final Set<String> PROFILES = Set.of("local", "dev", "stg", "prod");

    /** ConfigData 적용 뒤 외부 서비스 자동 설정보다 먼저 실행한다. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 11;
    }

    /** 설정값을 오류 문구에 포함하지 않고 잘못된 배포 구성을 거부한다. */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
            SpringApplication application) {
        String[] active = environment.getActiveProfiles();

        if (active.length != 1 || !PROFILES.contains(active[0])) {
            throw new IllegalStateException(
                    "SPRING_PROFILES_ACTIVE: select one deployment profile");
        }

        boolean local = "local".equals(active[0]);
        // 오류 비공개는 모든 환경에서 유지하고 local만 Flyway 자동 적용을 허용한다.
        for (String key : List.of("include-message", "include-binding-errors", "include-stacktrace",
                "include-path")) {
            requireValue(environment, "spring.web.error." + key, "never");
        }

        requireValue(environment, "spring.web.error.include-exception", "false");
        requireValue(environment, "spring.web.error.whitelabel.enabled", "false");
        if (!local) {
            requireValue(environment, "spring.flyway.enabled", "false");
        }

        requireValue(environment, "spring.sql.init.mode", "never");
        requireValue(environment, "spring.mvc.log-request-details", "false");
        requireValue(environment, "server.forward-headers-strategy", "none");
        requireValue(environment, "server.servlet.session.cookie.http-only", "true");
        requireValue(environment, "server.servlet.session.cookie.same-site", "lax");
        requireValue(environment, "server.servlet.session.cookie.path", "/");
        requireValue(environment, "server.servlet.session.tracking-modes", "cookie");
        requireValue(environment, "spring.session.data.redis.repository-type", "indexed");
        requireValue(environment, "spring.session.data.redis.configure-action", "none");
        requireValue(environment, "spring.session.data.redis.namespace",
                "starter:" + active[0] + ":session:v1");
        requireValue(environment, "server.servlet.session.cookie.name",
                local ? "local-session" : "__Host-session");
        requireValue(environment, "server.servlet.session.cookie.secure", Boolean.toString(!local));

        if (!Duration.ofMinutes(30).equals(DurationStyle.detectAndParse(
                environment.getRequiredProperty("spring.session.timeout")))) {
            throw new IllegalStateException("spring.session.timeout: required policy mismatch");
        }

        for (String key : List.of("spring.datasource.url", "spring.datasource.username",
                "spring.datasource.password", "spring.data.redis.host", "spring.data.redis.port",
                "spring.data.redis.username",
                "spring.data.redis.password")) {
            requireSecretOrSetting(environment, key);
        }

        String user = environment.getRequiredProperty("spring.datasource.username");

        if (Set.of("postgres", "root", "admin", "starter_bootstrap", "starter_migrator")
                .contains(user)) {
            throw new IllegalStateException("DB_USERNAME: application role required");
        }

        String redisUser = environment.getRequiredProperty("spring.data.redis.username");

        if (Set.of("default", "admin", "root").contains(redisUser)) {
            throw new IllegalStateException("REDIS_USERNAME: application ACL role required");
        }

        if (!local && ("testuser".equals(user) || "testuser".equals(redisUser)
                || "1234".equals(environment.getRequiredProperty("spring.datasource.password"))
                || "1234".equals(environment.getRequiredProperty("spring.data.redis.password")))) {
            throw new IllegalStateException("Public test credentials are local-only");
        }

        String originValue = environment.getRequiredProperty("app.public-origin");
        URI origin;

        try {
            origin = URI.create(originValue);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("APP_PUBLIC_ORIGIN: invalid origin");
        }

        if (origin.getHost() == null || origin.getUserInfo() != null || origin.getQuery() != null
                || origin.getFragment() != null || !origin.getRawPath().isEmpty()
                || !("https".equals(origin.getScheme())
                        || local && "http".equals(origin.getScheme())
                                && isLoopback(origin.getHost()))) {
            throw new IllegalStateException("APP_PUBLIC_ORIGIN: secure absolute origin required");
        }

        URI database = validateDatabaseUrl(environment.getRequiredProperty("spring.datasource.url"),
                local);

        if (local) {
            requireValue(environment, "server.address", "127.0.0.1");

            if (!isLoopback(database.getHost())
                    || !isLoopback(environment.getRequiredProperty("spring.data.redis.host"))) {
                throw new IllegalStateException("local: loopback services required");
            }
        } else {
            requireValue(environment, "spring.data.redis.ssl.enabled", "true");
        }

        if (environment.containsProperty("server.servlet.session.cookie.domain")) {
            throw new IllegalStateException("session cookie: Domain is forbidden");
        }

        for (String key : List.of("springdoc.api-docs.enabled", "springdoc.swagger-ui.enabled",
                "debug", "trace")) {
            if (environment.getProperty(key, Boolean.class, false)) {
                throw new IllegalStateException(key + ": disabled in foundation setup");
            }
        }

        // 루트 외 하위 logger의 DEBUG/TRACE도 함께 차단한다.
        for (var source : environment.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                Arrays.stream(enumerable.getPropertyNames())
                        .filter(name -> name.toLowerCase(Locale.ROOT).replace('_', '.')
                                .startsWith("logging.level."))
                        .forEach(name -> validateLogLevel(environment, name));
            }
        }
    }

    /** 정책 키가 완화되거나 누락되면 값 대신 키만 표시해 시작을 거부한다. */
    private static void requireValue(ConfigurableEnvironment environment, String key,
            String expected) {
        if (!expected.equalsIgnoreCase(environment.getProperty(key))) {
            throw new IllegalStateException(key + ": required policy mismatch");
        }
    }

    /** 빈값과 예제 placeholder가 서비스 자격으로 사용되는 것을 막는다. */
    private static void requireSecretOrSetting(ConfigurableEnvironment environment, String key) {
        String value = environment.getProperty(key);

        if (value == null || value.isBlank() || value.contains("${")
                || value.toLowerCase(Locale.ROOT).contains("change_me")
                || value.toLowerCase(Locale.ROOT).contains("placeholder")) {
            throw new IllegalStateException(key + ": external value required");
        }
    }

    /** 명시한 루프백만 local 평문 예외로 인정한다. */
    private static boolean isLoopback(String host) {
        return host != null && Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(host);
    }

    /** JDBC URL의 중복 TLS 옵션·커스텀 검증 우회·내장 자격을 거부한다. */
    private static URI validateDatabaseUrl(String value, boolean local) {
        try {
            if (!value.startsWith("jdbc:postgresql://")) {
                throw new IllegalArgumentException();
            }

            URI database = URI.create(value.substring(5));

            if (database.getHost() == null || database.getUserInfo() != null
                    || database.getFragment() != null) {
                throw new IllegalArgumentException();
            }

            boolean verified = false;
            Set<String> names = new java.util.HashSet<>();
            String query = database.getRawQuery();

            if (query != null) {
                for (String item : query.split("&")) {
                    String[] pair = item.split("=", 2);
                    String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8)
                            .toLowerCase(Locale.ROOT);
                    String setting = pair.length == 2
                            ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";

                    if (!names.add(name) || Set.of("password", "user", "sslfactory",
                            "sslhostnameverifier", "sslpassword", "sslfactoryarg").contains(name)) {
                        throw new IllegalArgumentException();
                    }

                    if ("sslmode".equals(name)) {
                        verified = "verify-full".equals(setting);
                    }
                }
            }

            if (!local && !verified) {
                throw new IllegalArgumentException();
            }

            return database;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "DB_URL: valid PostgreSQL URL with approved TLS required");
        }
    }

    /** 진단 수준 override로 SQL·요청 원문 수집을 켜지 못하게 한다. */
    private static void validateLogLevel(ConfigurableEnvironment environment, String key) {
        String level = environment.getProperty(key, "INFO");

        if ("DEBUG".equalsIgnoreCase(level) || "TRACE".equalsIgnoreCase(level)) {
            throw new IllegalStateException(key + ": verbose logging forbidden");
        }
    }
}
