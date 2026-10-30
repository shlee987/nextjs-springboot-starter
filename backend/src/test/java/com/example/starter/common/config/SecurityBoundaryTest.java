package com.example.starter.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.TestPolicies;
import com.example.starter.TestTransactions;
import com.example.starter.audit.mapper.AuditMapper;
import com.example.starter.audit.service.AuditWriter;
import com.example.starter.audit.service.DenialAuditService;
import com.example.starter.auth.config.AuthAuthenticationConverter;
import com.example.starter.auth.model.AuthLogin;
import com.example.starter.auth.store.AuthStateStore;
import com.example.starter.auth.web.AuthController;
import com.example.starter.auth.web.AuthCookies;
import com.example.starter.common.web.ApiExceptionHandler;
import com.example.starter.common.web.ProblemResponses;
import com.example.starter.common.web.RequestBoundaryFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

/** 실제 JWT/CSRF chain을 격리 컨텍스트에서 실행한다. DB·Redis·키 생성은 하지 않는다. */
class SecurityBoundaryTest {
    /** CSRF 발급은 세션 없이 동작하고 만료 access는 공개 login의 DTO 검증을 막지 않는다. */
    @Test
    void publicCsrfAndLoginDoNotUseJwtOrSession() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.setEnvironment(TestPolicies.environment());
            context.register(TestSecurity.class, SecurityConfiguration.class);
            context.refresh();
            var problems = context.getBean(ProblemResponses.class);
            var controller =
                    new AuthController(
                            null,
                            null,
                            context.getBean(AuthCookies.class),
                            context.getBean(CookieCsrfTokenRepository.class),
                            context.getBean(JsonMapper.class));
            var mvc =
                    MockMvcBuilders.standaloneSetup(controller)
                            .setControllerAdvice(
                                    new ApiExceptionHandler(
                                            problems, context.getBean(DenialAuditService.class)))
                            .addFilters(
                                    new RequestBoundaryFilter(
                                            problems,
                                            TestPolicies.environment(),
                                            context.getBean(DenialAuditService.class),
                                            context.getBean(AuthStateStore.class),
                                            context.getBean(Clock.class)),
                                    context.getBean("springSecurityFilterChain", Filter.class))
                            .build();
            var issued = mvc.perform(MockMvcRequestBuilders.get("/api/auth/csrf")).andReturn();
            assertEquals(200, issued.getResponse().getStatus());
            Assertions.assertNull(issued.getRequest().getSession(false));
            var cookie = issued.getResponse().getCookie("local-csrf");
            assertTrue(cookie.isHttpOnly());
            String token =
                    context.getBean(JsonMapper.class)
                            .readTree(issued.getResponse().getContentAsString())
                            .at("/data/token")
                            .asString();
            var login =
                    mvc.perform(
                                    MockMvcRequestBuilders.post("/api/auth/login")
                                            .header("Origin", "http://localhost:3000")
                                            .header("X-CSRF-TOKEN", token)
                                            .cookie(
                                                    cookie,
                                                    new Cookie(
                                                            "local-access",
                                                            "synthetic-expired-access"))
                                            .contentType("application/json")
                                            .content(
                                                    "{\"loginId\":\"INVALID!\",\"password\":\"x\"}"))
                            .andReturn();
            assertEquals(400, login.getResponse().getStatus());
            assertTrue(login.getResponse().getContentAsString().contains("VALIDATION_FAILED"));
            Assertions.assertNull(login.getRequest().getSession(false));
            var wrongMethod =
                    mvc.perform(MockMvcRequestBuilders.get("/api/auth/login")).andReturn();
            assertEquals(401, wrongMethod.getResponse().getStatus());
        }
    }

    /** 세션 SecurityContext는 API 인증을 승계하지 않고 origin/CSRF를 엄격 구분한다. */
    @Test
    void statelessApiRejectsSessionAndUnsafeOrigin() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.setEnvironment(TestPolicies.environment());
            context.register(TestSecurity.class, SecurityConfiguration.class);
            context.refresh();
            var problems = context.getBean(ProblemResponses.class);
            MockMvc mvc =
                    MockMvcBuilders.standaloneSetup(new EmptyController())
                            .addFilters(
                                    new RequestBoundaryFilter(
                                            problems,
                                            TestPolicies.environment(),
                                            context.getBean(DenialAuditService.class),
                                            context.getBean(AuthStateStore.class),
                                            context.getBean(Clock.class)),
                                    context.getBean("springSecurityFilterChain", Filter.class))
                            .build();
            var denied =
                    mvc.perform(MockMvcRequestBuilders.get("/api/private").accept("text/html"))
                            .andReturn()
                            .getResponse();
            assertEquals(401, denied.getStatus());
            assertTrue(denied.getContentAsString().contains("UNAUTHENTICATED"));
            assertFalse(denied.getContentAsString().contains("<html"));
            var origin =
                    mvc.perform(MockMvcRequestBuilders.post("/api/private"))
                            .andReturn()
                            .getResponse();
            assertEquals(403, origin.getStatus());
            assertTrue(origin.getContentAsString().contains("ORIGIN_REJECTED"));
            var csrf =
                    mvc.perform(
                                    MockMvcRequestBuilders.post("/api/private")
                                            .header("Origin", "http://localhost:3000"))
                            .andReturn()
                            .getResponse();
            assertEquals(403, csrf.getStatus());
            assertTrue(csrf.getContentAsString().contains("CSRF_TOKEN_MISSING"));
            assertEquals("nosniff", csrf.getHeader("X-Content-Type-Options"));
            var invalid =
                    mvc.perform(
                                    MockMvcRequestBuilders.post("/api/private")
                                            .header("Origin", "http://localhost:3000")
                                            .cookie(new Cookie("local-csrf", "synthetic-cookie"))
                                            .header("X-CSRF-TOKEN", "synthetic-invalid"))
                            .andReturn()
                            .getResponse();
            assertEquals(403, invalid.getStatus());
            assertTrue(invalid.getContentAsString().contains("CSRF_TOKEN_INVALID"));
            var session = new MockHttpSession();
            session.setAttribute(
                    "SPRING_SECURITY_CONTEXT",
                    new SecurityContextImpl(
                            UsernamePasswordAuthenticationToken.authenticated(
                                    "synthetic-subject", null, List.of())));
            var sessionOnly =
                    mvc.perform(MockMvcRequestBuilders.get("/api/private").session(session))
                            .andReturn()
                            .getResponse();
            assertEquals(401, sessionOnly.getStatus());
            assertTrue(context.getBeansOfType(UserDetailsService.class).isEmpty());
        }
    }

    /** 보호된 JWT GET/POST가 로그인 CSRF 쿠키를 지우지 않아 같은 XOR header로 쓰기가 성공한다. */
    @Test
    void repeatedJwtAuthenticationPreservesCsrfUntilExplicitLoginOrLogout() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.setEnvironment(TestPolicies.environment());
            context.register(JwtTestSecurity.class, SecurityConfiguration.class);
            context.refresh();
            var mvc =
                    MockMvcBuilders.standaloneSetup(
                                    new ReadWriteController(),
                                    new AuthController(
                                            null,
                                            null,
                                            context.getBean(AuthCookies.class),
                                            context.getBean(CookieCsrfTokenRepository.class),
                                            context.getBean(JsonMapper.class)))
                            .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                            .build();
            var csrf =
                    mvc.perform(MockMvcRequestBuilders.get("/api/auth/csrf"))
                            .andReturn()
                            .getResponse();
            var csrfCookie = csrf.getCookie("local-csrf");
            String token =
                    context.getBean(JsonMapper.class)
                            .readTree(csrf.getContentAsString())
                            .at("/data/token")
                            .asString();
            var access = new Cookie("local-access", "synthetic-valid");
            var read =
                    mvc.perform(
                                    MockMvcRequestBuilders.get("/api/private")
                                            .cookie(csrfCookie, access))
                            .andReturn();
            assertEquals(200, read.getResponse().getStatus());
            Assertions.assertNull(read.getResponse().getCookie("local-csrf"));
            var write =
                    mvc.perform(
                                    MockMvcRequestBuilders.post("/api/private")
                                            .cookie(csrfCookie, access)
                                            .header("Origin", "http://localhost:3000")
                                            .header("X-CSRF-TOKEN", token))
                            .andReturn();
            assertEquals(200, write.getResponse().getStatus());
            Assertions.assertNull(write.getResponse().getCookie("local-csrf"));
            Assertions.assertNull(write.getRequest().getSession(false));
        }
    }

    /** 실제 보안 chain을 쓰되 서명/저장소 경계만 합성한다. */
    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @Import(AuthAuthenticationConverter.class)
    static class JwtTestSecurity extends TestSecurity {
        /** 같은 sid를 반복 복원하는 실제 converter 입력이다. */
        @Bean
        @Override
        AuthStateStore states() {
            long now = clock().millis();
            var login =
                    new AuthLogin(
                            "synthetic-sid",
                            1,
                            now,
                            now,
                            now,
                            now + 1800000,
                            now + 28800000,
                            null,
                            null,
                            "[\"USER_READ_SELF\"]",
                            "synthetic-hash");
            return (AuthStateStore)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {AuthStateStore.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "require" -> login;
                                        case "consume" -> 0L;
                                        case "touch" -> null;
                                        default -> throw new AssertionError(method.getName());
                                    });
        }

        /** 키 생성 없이 JWT 인증 성공 뒤 기본 Spring 전략이 실제 실행되게 한다. */
        @Bean
        @Override
        JwtDecoder decoder() {
            return token -> {
                if (token.equals("synthetic-expired") || token.equals("synthetic-invalid")) {
                    throw new JwtValidationException(
                            "Synthetic rejected token", List.of(new OAuth2Error("invalid_token")));
                }

                return Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .subject("1")
                        .issuedAt(clock().instant())
                        .expiresAt(clock().instant().plusSeconds(600))
                        .claim("sid", "synthetic-sid")
                        .claim("auth_time", clock().instant().getEpochSecond())
                        .build();
            };
        }
    }

    /** 제품에는 추가하지 않는 격리 읽기/쓰기 응답이다. */
    @RestController
    static class ReadWriteController {
        /** 동일 route에서 실제 GET chain을 통과한다. */
        @GetMapping("/api/private")
        public String read() {
            return "ok";
        }

        /** XOR CSRF가 유효할 때만 도달한다. */
        @PostMapping("/api/private")
        public String write() {
            return "ok";
        }
    }

    /** DB/키 공급 bean 없이 실제 보안 구성 의존성만 제공한다. */
    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @Import(AuthAuthenticationConverter.class)
    static class TestSecurity {
        /** 실제 JSON/오류 경계를 사용한다. */
        @Bean
        JsonMapper json() {
            return JsonMapper.builder().build();
        }

        /** 고정된 UTC 시간이다. */
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC);
        }

        /** YAML 설정을 그대로 binding한다. */
        @Bean
        AppProperties properties() {
            return TestPolicies.local();
        }

        /** 검증한 쿠키 구성만 제공한다. */
        @Bean
        AuthCookies cookies(Clock clock) {
            return new AuthCookies(TestPolicies.environment(), clock);
        }

        /** 실제 low-level writer를 DB 없는 Mapper 대역에 연결한다. */
        @Bean
        DenialAuditService audit(Clock clock, AppProperties properties) {
            AuditMapper mapper =
                    (AuditMapper)
                            Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[] {AuditMapper.class},
                                    (proxy, method, args) -> 1);

            return new DenialAuditService(
                    new AuditWriter(
                            mapper,
                            clock,
                            properties,
                            new TestTransactions(),
                            TestPolicies.references()));
        }

        /** 이 시험은 외부 상태를 변경하지 않고 한도 허용 결과만 제공한다. */
        @Bean
        AuthStateStore states() {
            return (AuthStateStore)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {AuthStateStore.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("consume")) {
                                    return 0L;
                                }

                                throw new AssertionError(
                                        "Unexpected state access: " + method.getName());
                            });
        }

        /** 누락 access 시험에서 decoder가 실행되면 즉시 실패한다. */
        @Bean
        JwtDecoder decoder() {
            return token -> {
                throw new AssertionError("Unexpected JWT decoding");
            };
        }

        /** 공개 오류 형식을 제품 mapper로 출력한다. */
        @Bean
        ProblemResponses problems(JsonMapper json) {
            return new ProblemResponses(json);
        }
    }

    /** 시험용 빈 MVC 경계이며 제품에 장애 endpoint를 추가하지 않는다. */
    @RestController
    static class EmptyController {}
}
