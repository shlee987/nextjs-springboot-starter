package com.example.starter.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisProperties;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.session.web.http.CookieSerializer;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;

/** 외부 서비스에 연결하지 않고 배포 설정과 공유 세션 JSON 계약을 검사한다. */
class FoundationConfigurationTest {
    /** 오류 비공개는 모든 환경에 강제하고 Flyway 자동 적용은 local만 허용한다. */
    @Test
    void allProfilesRejectDiagnosticAndDdlOverrides() throws Exception {
        for (String profile : List.of("local", "dev", "stg", "prod")) {
            MockEnvironment environment = environment(profile);
            EnvironmentGuard guard = new EnvironmentGuard();
            guard.postProcessEnvironment(environment, new SpringApplication());
            SessionDataRedisProperties properties = Binder.get(environment)
                    .bind("spring.session.data.redis", SessionDataRedisProperties.class).get();
            assertEquals("INDEXED", properties.getRepositoryType().name());
            assertEquals("starter:" + profile + ":session:v1", properties.getNamespace());

            for (String key : List.of("spring.web.error.include-message",
                    "spring.web.error.include-binding-errors",
                    "spring.web.error.include-stacktrace",
                    "spring.web.error.include-path")) {
                environment.withProperty(key, "always");
                assertThrows(IllegalStateException.class,
                        () -> guard.postProcessEnvironment(environment, new SpringApplication()));
                environment.withProperty(key, "never");
            }

            environment.withProperty("spring.flyway.enabled", "true");

            if ("local".equals(profile)) {
                guard.postProcessEnvironment(environment, new SpringApplication());
            } else {
                assertThrows(IllegalStateException.class,
                        () -> guard.postProcessEnvironment(environment, new SpringApplication()));
            }
        }
    }

    /** local은 공개 테스트 기본값으로 완성되고 환경변수로 접속점과 자격을 바꿀 수 있다. */
    @Test
    void localDefaultsConnectWithoutExternalValuesAndAllowOverrides() throws Exception {
        MockEnvironment environment = deploymentEnvironment("local");
        EnvironmentGuard guard = new EnvironmentGuard();
        guard.postProcessEnvironment(environment, new SpringApplication());
        assertEquals("jdbc:postgresql://127.0.0.1:25432/starter_local",
                environment.getProperty("spring.datasource.url"));
        assertEquals("testuser", environment.getProperty("spring.datasource.username"));
        assertEquals("1234", environment.getProperty("spring.datasource.password"));
        assertEquals("127.0.0.1", environment.getProperty("spring.data.redis.host"));
        assertEquals("26379", environment.getProperty("spring.data.redis.port"));
        assertEquals("testuser", environment.getProperty("spring.data.redis.username"));
        assertEquals("1234", environment.getProperty("spring.data.redis.password"));
        assertEquals("http://localhost:3000", environment.getProperty("app.public-origin"));
        assertEquals("true", environment.getProperty("spring.flyway.enabled"));
        assertEquals("classpath:db/migration", environment.getProperty("spring.flyway.locations"));
        assertEquals("false", environment.getProperty("spring.flyway.fail-on-missing-locations"));
        environment.withProperty("DB_URL", "jdbc:postgresql://localhost:25433/alternate");
        environment.withProperty("DB_USERNAME", "synthetic_app");
        environment.withProperty("DB_PASSWORD", "synthetic-db-secret");
        environment.withProperty("REDIS_HOST", "localhost");
        environment.withProperty("REDIS_PORT", "26380");
        environment.withProperty("REDIS_USERNAME", "synthetic_session");
        environment.withProperty("REDIS_PASSWORD", "synthetic-redis-secret");
        environment.withProperty("APP_PUBLIC_ORIGIN", "http://127.0.0.1:3001");
        guard.postProcessEnvironment(environment, new SpringApplication());
        assertEquals("jdbc:postgresql://localhost:25433/alternate",
                environment.getProperty("spring.datasource.url"));
        assertEquals("synthetic_app", environment.getProperty("spring.datasource.username"));
        assertEquals("synthetic-db-secret", environment.getProperty("spring.datasource.password"));
        assertEquals("localhost", environment.getProperty("spring.data.redis.host"));
        assertEquals("26380", environment.getProperty("spring.data.redis.port"));
        assertEquals("synthetic_session", environment.getProperty("spring.data.redis.username"));
        assertEquals("synthetic-redis-secret",
                environment.getProperty("spring.data.redis.password"));
        assertEquals("http://127.0.0.1:3001", environment.getProperty("app.public-origin"));
    }

    /** 공유 환경은 local 기본값을 상속하지 않고 각 필수 외부값 누락 시 실패한다. */
    @Test
    void sharedProfilesRequireEveryExternalValueWithoutLocalFallback() throws Exception {
        EnvironmentGuard guard = new EnvironmentGuard();

        for (String profile : List.of("dev", "stg", "prod")) {
            MockEnvironment bare = deploymentEnvironment(profile);
            assertEquals("false", bare.getProperty("spring.flyway.enabled"));
            assertEquals("true", bare.getProperty("spring.data.redis.ssl.enabled"));
            assertThrows(RuntimeException.class,
                    () -> guard.postProcessEnvironment(bare, new SpringApplication()));

            for (String missing : List.of("DB_URL", "DB_USERNAME", "DB_PASSWORD", "REDIS_HOST",
                    "REDIS_PORT", "REDIS_USERNAME", "REDIS_PASSWORD", "APP_PUBLIC_ORIGIN")) {
                MockEnvironment environment = environment(profile);
                MapPropertySource synthetic = (MapPropertySource) environment.getPropertySources()
                        .get("synthetic");
                synthetic.getSource().remove(missing);
                assertThrows(RuntimeException.class,
                        () -> guard.postProcessEnvironment(environment, new SpringApplication()),
                        profile + ":" + missing);
            }
        }
    }

    /** 공개 local 자격이 명시적으로 공유 환경에 주입되어도 시작하지 않는다. */
    @Test
    void publicLocalCredentialsAreRejectedInSharedProfiles() throws Exception {
        EnvironmentGuard guard = new EnvironmentGuard();

        for (String profile : List.of("dev", "stg", "prod")) {
            for (String key : List.of("spring.datasource.username", "spring.data.redis.username",
                    "spring.datasource.password", "spring.data.redis.password")) {
                MockEnvironment environment = environment(profile);
                environment.withProperty(key, key.endsWith("username") ? "testuser" : "1234");
                assertThrows(IllegalStateException.class,
                        () -> guard.postProcessEnvironment(environment, new SpringApplication()));
            }
        }
    }

    /** 프로파일 누락·중복과 비밀 placeholder는 안전한 키 이름으로만 실패한다. */
    @Test
    void invalidEnvironmentFailsWithoutEchoingValue() throws Exception {
        EnvironmentGuard guard = new EnvironmentGuard();
        MockEnvironment missing = new MockEnvironment();
        assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(missing, new SpringApplication()));
        MockEnvironment environment = environment("local");
        environment.setActiveProfiles("local", "prod");
        assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(environment, new SpringApplication()));
        environment.setActiveProfiles("local");
        environment.withProperty("spring.datasource.password", "SYNTHETIC_PLACEHOLDER_SECRET");
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(environment, new SpringApplication()));
        assertFalse(exception.toString().contains("SYNTHETIC_PLACEHOLDER_SECRET"));
        MockEnvironment insecure = environment("prod");
        insecure.withProperty("app.public-origin", "http://example.invalid");
        assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(insecure, new SpringApplication()));
    }

    /** CSRF·SecurityContext·Session 메타데이터를 JSON으로 복원하며 임의 타입은 거부한다. */
    @Test
    void sessionJsonRoundTripsApprovedTypesOnly() {
        RedisSerializer<Object> serializer = new SessionConfiguration()
                .springSessionDefaultRedisSerializer();
        DefaultCsrfToken csrf = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "synthetic-token");
        DefaultCsrfToken restored = (DefaultCsrfToken) serializer.deserialize(
                serializer.serialize(csrf));
        assertEquals(csrf.getToken(), restored.getToken());
        SecurityContextImpl context = new SecurityContextImpl(
                UsernamePasswordAuthenticationToken.authenticated("synthetic-subject", null,
                        List.of(new SimpleGrantedAuthority("ROLE_SYNTHETIC"))));
        SecurityContextImpl security = (SecurityContextImpl) serializer.deserialize(
                serializer.serialize(context));
        assertEquals("synthetic-subject", security.getAuthentication().getName());
        assertEquals(context.getAuthentication().getAuthorities(),
                security.getAuthentication().getAuthorities());
        assertEquals(1_790_000_000_000L,
                serializer.deserialize(serializer.serialize(1_790_000_000_000L)));
        assertEquals(1800, serializer.deserialize(serializer.serialize(1800)));
        assertEquals(1L, serializer.deserialize(serializer.serialize(1L)));
        HashMap<String, Object> event = new HashMap<>();
        event.put("creationTime", 1L);
        event.put("sessionAttr:csrf", csrf);
        Map<?, ?> restoredEvent = (Map<?, ?>) serializer.deserialize(serializer.serialize(event));
        assertEquals(1L, restoredEvent.get("creationTime"));
        assertEquals(csrf.getToken(),
                ((DefaultCsrfToken) restoredEvent.get("sessionAttr:csrf")).getToken());
        assertThrows(RuntimeException.class,
                () -> serializer.serialize(new java.io.File("synthetic")));
        assertThrows(RuntimeException.class, () -> serializer.deserialize(
                "{\"@class\":\"java.io.File\",\"path\":\"synthetic\"}"
                        .getBytes(StandardCharsets.UTF_8)));
        event.put("unapproved", new java.io.File("synthetic"));
        assertThrows(RuntimeException.class,
                () -> serializer.deserialize(serializer.serialize(event)));
        assertTrue(new String(serializer.serialize(csrf), StandardCharsets.UTF_8).startsWith("{"));
    }

    /** local 예외와 운영 쿠키의 속성을 실제 Set-Cookie 출력으로 비교한다. */
    @Test
    void sessionCookiePolicyMatchesEachProfile() throws Exception {
        for (String profile : List.of("local", "dev", "stg", "prod")) {
            var serializer = new SessionConfiguration().cookieSerializer(environment(profile));
            MockHttpServletResponse response = new MockHttpServletResponse();
            serializer.writeCookieValue(new CookieSerializer.CookieValue(
                    new MockHttpServletRequest(), response, "synthetic-id"));
            String cookie = response.getHeader("Set-Cookie");
            assertTrue(cookie.contains("HttpOnly"));
            assertTrue(cookie.contains("SameSite=Lax"));
            assertTrue(cookie.contains("Path=/"));
            assertFalse(cookie.contains("Domain="));
            assertFalse(cookie.contains("Max-Age="));
            assertEquals(!"local".equals(profile), cookie.contains("Secure"));
            assertTrue(cookie.startsWith("local".equals(profile)
                    ? "local-session=" : "__Host-session="));
        }
    }

    /** 비검증 TLS나 중복 옵션·관리자 자격으로 운영 보호를 우회할 수 없다. */
    @Test
    void databaseTlsAndCredentialsRejectUnsafeOverrides() throws Exception {
        EnvironmentGuard guard = new EnvironmentGuard();

        for (String query : List.of("sslmode=disable", "sslmode=verify-full&sslmode=disable",
                "sslmode=verify-full&sslfactory=untrusted",
                "sslmode=verify-full&password=canary")) {
            MockEnvironment environment = environment("prod");
            environment.withProperty("spring.datasource.url",
                    "jdbc:postgresql://database.example.invalid/starter?" + query);
            assertThrows(IllegalStateException.class,
                    () -> guard.postProcessEnvironment(environment, new SpringApplication()));
        }
    }

    /** 최종 HTTP JSON mapper에서 미등록 필드·중복 키·과도한 깊이를 거부한다. */
    @Test
    void jsonParserRejectsAmbiguousAndOversizedStructure() {
        JsonConfiguration configuration = new JsonConfiguration();
        var factory = JsonFactory.builder();
        configuration.strictJsonStructure().customize(factory);
        var builder = JsonMapper.builder(factory.build());
        configuration.strictJsonFields().customize(builder);
        JsonMapper mapper = builder.build();
        assertThrows(RuntimeException.class,
                () -> mapper.readValue("{\"unknown\":1}", SyntheticJsonInput.class));
        assertThrows(RuntimeException.class,
                () -> mapper.readTree("{\"name\":1,\"name\":2}"));
        assertThrows(RuntimeException.class,
                () -> mapper.readTree("[".repeat(33) + "0" + "]".repeat(33)));
    }

    /** HTTP JSON 설정만 검증하는 비공개 합성 DTO다. */
    record SyntheticJsonInput(String name) {
    }

    /** 실제 공통/프로파일 YAML만 조합하며 외부 서비스에는 접속하지 않는다. */
    private MockEnvironment deploymentEnvironment(String profile) throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        environment.withProperty("spring.profiles.active", profile);
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        environment.getPropertySources().addLast(loader.load("common",
                new ClassPathResource("application.yml")).getFirst());
        environment.getPropertySources().addBefore("common", loader.load("profile",
                new ClassPathResource("application-" + profile + ".yml")).getFirst());

        return environment;
    }

    /** 프로파일 YAML에 가상 외부 설정을 주입해 환경별 정책을 검사한다. */
    private MockEnvironment environment(String profile) throws Exception {
        MockEnvironment environment = deploymentEnvironment(profile);
        Map<String, Object> synthetic = new HashMap<>(Map.of(
                "DB_URL", "local".equals(profile)
                        ? "jdbc:postgresql://127.0.0.1:25432/starter_local"
                        : "jdbc:postgresql://database.example.invalid/starter?sslmode=verify-full",
                "DB_USERNAME", "synthetic_app", "DB_PASSWORD", "synthetic-not-a-real-secret",
                "REDIS_HOST", "local".equals(profile) ? "127.0.0.1" : "redis.example.invalid",
                "REDIS_USERNAME", "synthetic_session", "REDIS_PASSWORD", "synthetic-redis-secret",
                "REDIS_PORT", "local".equals(profile) ? "26379" : "6379",
                "APP_PUBLIC_ORIGIN", "local".equals(profile)
                        ? "http://localhost:3000" : "https://example.invalid",
                "spring.profiles.active", profile));
        environment.getPropertySources().addFirst(new MapPropertySource("synthetic", synthetic));

        return environment;
    }
}
