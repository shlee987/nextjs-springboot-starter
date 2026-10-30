package com.example.starter.common.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;

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
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {
        String[] active = environment.getActiveProfiles();

        if (active.length != 1 || !PROFILES.contains(active[0])) {
            throw new IllegalStateException(
                    "SPRING_PROFILES_ACTIVE: select one deployment profile");
        }

        boolean local = "local".equals(active[0]);
        // 오류 비공개는 모든 환경에서 유지하고 local만 Flyway 자동 적용을 허용한다.
        for (String key :
                List.of(
                        "include-message",
                        "include-binding-errors",
                        "include-stacktrace",
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
        if (!local) {
            requireValue(environment, "spring.main.log-startup-info", "false");
        }

        for (String key : List.of("debug", "trace")) {
            if (environment.getProperty(key, Boolean.class, false)) {
                throw new IllegalStateException(
                        key + ": configure only approved local logger levels");
            }
        }
        requireValue(environment, "server.forward-headers-strategy", "none");
        requireExactValue(environment, "spring.datasource.hikari.schema", "public");
        requireValue(
                environment,
                "mybatis.configuration.log-impl",
                "org.apache.ibatis.logging.nologging.NoLoggingImpl");
        requireValue(environment, "mybatis.configuration.map-underscore-to-camel-case", "true");
        requireValue(
                environment,
                "mybatis.configuration.arg-name-based-constructor-auto-mapping",
                "true");
        requireValue(environment, "mybatis.configuration.shrink-whitespaces-in-sql", "false");
        requireValue(environment, "mybatis.configuration.default-executor-type", "SIMPLE");
        requireValue(environment, "mybatis.configuration.cache-enabled", "false");
        requireValue(environment, "mybatis.configuration.local-cache-scope", "STATEMENT");
        requireValue(environment, "server.servlet.session.tracking-modes", "cookie");
        String store =
                environment.getProperty("app.auth.state-store", "redis").toLowerCase(Locale.ROOT);

        if (!Set.of("redis", "jdbc").contains(store)) {
            throw new IllegalStateException("app.auth.state-store: unsupported store");
        }

        boolean redis = "redis".equals(store);

        for (String key :
                List.of(
                        "spring.datasource.url",
                        "spring.datasource.username",
                        "spring.datasource.password")) {
            requireSecretOrSetting(environment, key);
        }

        if (redis) {
            for (String key : List.of("host", "port", "username", "password")) {
                requireSecretOrSetting(environment, "spring.data.redis." + key);
            }
        }

        String user = environment.getRequiredProperty("spring.datasource.username");

        if (Set.of("postgres", "root", "admin", "starter_bootstrap", "starter_migrator")
                .contains(user)) {
            throw new IllegalStateException("DB_USERNAME: application role required");
        }

        String redisUser =
                redis ? environment.getRequiredProperty("spring.data.redis.username") : "";

        if (Set.of("default", "admin", "root").contains(redisUser)) {
            throw new IllegalStateException("REDIS_USERNAME: application ACL role required");
        }

        if (!local
                && ("testuser".equals(user)
                        || "testuser".equals(redisUser)
                        || "1234"
                                .equals(
                                        environment.getRequiredProperty(
                                                "spring.datasource.password"))
                        || redis
                                && "1234"
                                        .equals(
                                                environment.getProperty(
                                                        "spring.data.redis.password")))) {
            throw new IllegalStateException("Public test credentials are local-only");
        }

        String originValue = environment.getRequiredProperty("app.public-origin");
        URI origin;

        try {
            origin = URI.create(originValue);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("APP_PUBLIC_ORIGIN: invalid origin");
        }

        if (origin.getHost() == null
                || origin.getUserInfo() != null
                || origin.getQuery() != null
                || origin.getFragment() != null
                || !origin.getRawPath().isEmpty()
                || !("https".equals(origin.getScheme())
                        || local
                                && "http".equals(origin.getScheme())
                                && isLoopback(origin.getHost()))) {
            throw new IllegalStateException("APP_PUBLIC_ORIGIN: secure absolute origin required");
        }

        URI database =
                validateDatabaseUrl(
                        environment.getRequiredProperty("spring.datasource.url"), local);

        if (local) {
            requireValue(environment, "server.address", "127.0.0.1");

            if (!isLoopback(database.getHost())
                    || redis && !isLoopback(environment.getProperty("spring.data.redis.host"))) {
                throw new IllegalStateException("local: loopback services required");
            }
        } else if (redis) {
            requireValue(environment, "spring.data.redis.ssl.enabled", "true");
        }

        if (environment.containsProperty("server.servlet.session.cookie.domain")) {
            throw new IllegalStateException("session cookie: Domain is forbidden");
        }

        if (!local) {
            requireSecretOrSetting(environment, "app.audit.login-reference-key-location");
            for (String key :
                    List.of(
                            "springdoc.api-docs.enabled",
                            "springdoc.swagger-ui.enabled",
                            "app.bootstrap.local-data-enabled",
                            "app.logging.sql.enabled",
                            "debug",
                            "trace")) {
                if (environment.getProperty(key, Boolean.class, false)) {
                    throw new IllegalStateException(key + ": local-only setting");
                }
            }

            for (String key :
                    List.of(
                            "issuer",
                            "audience",
                            "active-key-id",
                            "private-key-location",
                            "public-keys-location")) {
                requireSecretOrSetting(environment, "app.auth.jwt." + key);
            }
        }

        requireExactValue(environment, "app.auth.namespace", "starter:" + active[0] + ":auth");
        requireExactValue(
                environment,
                "app.auth.prelogin-session.namespace",
                "starter:" + active[0] + ":prelogin");
        environment
                .getPropertySources()
                .addLast(
                        new MapPropertySource(
                                "foundation-defaults",
                                Map.of(
                                        "app.server-id",
                                        defaultServerId(environment),
                                        "app.cookies.secure",
                                        !local,
                                        "app.cookies.prefix",
                                        local ? "local-" : "__Host-")));
        requireValue(environment, "app.cookies.secure", Boolean.toString(!local));
        requireExactValue(environment, "app.cookies.prefix", local ? "local-" : "__Host-");
        requireValue(environment, "app.logging.file-enabled", Boolean.toString(!local));

        // 루트 외 하위 logger의 DEBUG/TRACE도 함께 차단한다.
        for (var source : environment.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                Arrays.stream(enumerable.getPropertyNames())
                        .filter(
                                name ->
                                        name.toLowerCase(Locale.ROOT)
                                                .replace('_', '.')
                                                .startsWith("logging.level."))
                        .forEach(
                                name -> {
                                    String loggerKey =
                                            name.toLowerCase(Locale.ROOT).replace('_', '.');
                                    if (!(local
                                            && (loggerKey.startsWith(
                                                            "logging.level.com.example.starter")
                                                    || loggerKey.equals(
                                                            "logging.level.starter.sql")))) {
                                        validateLogLevel(environment, name);
                                    }
                                });
            }
        }
    }

    /** 정책 키가 완화되거나 누락되면 값 대신 키만 표시해 시작을 거부한다. */
    private static void requireValue(
            ConfigurableEnvironment environment, String key, String expected) {
        if (!expected.equalsIgnoreCase(environment.getProperty(key))) {
            throw new IllegalStateException(key + ": required policy mismatch");
        }
    }

    /** cookie prefix와 물리 namespace의 대소문자 차이를 보안 정책 우회로 허용하지 않는다. */
    private static void requireExactValue(
            ConfigurableEnvironment environment, String key, String expected) {
        if (!expected.equals(environment.getProperty(key))) {
            throw new IllegalStateException(key + ": required policy mismatch");
        }
    }

    /** DNS 조회 없이 host/설정 port/시작 UUID로 노드별 안전한 로그 경로 이름을 만든다. */
    private static String defaultServerId(ConfigurableEnvironment environment) {
        String host =
                environment.getProperty(
                        "HOSTNAME", environment.getProperty("COMPUTERNAME", "node"));
        host = host.replaceAll("[^A-Za-z0-9._-]", "-");
        host = host.substring(0, Math.min(40, host.length()));

        try {
            int port = Integer.parseInt(environment.getProperty("server.port", "8080"));

            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException();
            }

            return "node-" + host + "-" + port + "-" + UUID.randomUUID();
        } catch (IllegalArgumentException invalidPort) {
            throw new IllegalStateException("server.port: valid port required");
        }
    }

    /** 빈값과 예제 placeholder가 서비스 자격으로 사용되는 것을 막는다. */
    private static void requireSecretOrSetting(ConfigurableEnvironment environment, String key) {
        String value;

        try {
            value = environment.getProperty(key);
        } catch (RuntimeException invalidPlaceholder) {
            throw new IllegalStateException(key + ": external value required");
        }

        if (value == null
                || value.isBlank()
                || value.contains("${")
                || value.toLowerCase(Locale.ROOT).contains("change_me")
                || value.toLowerCase(Locale.ROOT).contains("placeholder")) {
            throw new IllegalStateException(key + ": external value required");
        }
    }

    /** 명시한 루프백만 local 평문 예외로 인정한다. */
    public static boolean isLoopback(String host) {
        return host != null && Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(host);
    }

    /** JDBC URL의 중복 TLS 옵션·커스텀 검증 우회·내장 자격을 거부한다. */
    public static URI validateDatabaseUrl(String value, boolean local) {
        try {
            if (!value.startsWith("jdbc:postgresql://")) {
                throw new IllegalArgumentException();
            }

            URI database = URI.create(value.substring(5));

            if (database.getHost() == null
                    || database.getUserInfo() != null
                    || database.getFragment() != null) {
                throw new IllegalArgumentException();
            }

            boolean verified = false;
            Set<String> names = new HashSet<>();
            String query = database.getRawQuery();

            if (query != null) {
                for (String item : query.split("&")) {
                    String[] pair = item.split("=", 2);
                    String name =
                            URLDecoder.decode(pair[0], StandardCharsets.UTF_8)
                                    .toLowerCase(Locale.ROOT);
                    String setting =
                            pair.length == 2
                                    ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                                    : "";

                    if (!names.add(name)
                            || Set.of(
                                            "password",
                                            "user",
                                            "sslfactory",
                                            "sslhostnameverifier",
                                            "sslpassword",
                                            "sslfactoryarg")
                                    .contains(name)) {
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
