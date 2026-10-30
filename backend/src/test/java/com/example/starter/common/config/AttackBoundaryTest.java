package com.example.starter.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.TestPolicies;
import com.example.starter.audit.service.DenialAuditService;
import com.example.starter.auth.config.ExternalAuthenticationConfiguration;
import com.example.starter.auth.store.AuthStateStore;
import com.example.starter.auth.web.AuthController;
import com.example.starter.auth.web.AuthCookies;
import com.example.starter.common.web.ProblemResponses;
import com.example.starter.common.web.RequestBoundaryFilter;
import com.example.starter.common.web.ScalarQueryFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.Part;
import java.time.Clock;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;

/** 외부 저장소 없이 실제 firewall·인증·JSON 경계를 검사한다. */
class AttackBoundaryTest {
    /** 실제 FilterChainProxy의 비정상 경로·메서드 거부도 안전한 오류로 반환한다. */
    @Test
    void strictFirewallRejectsUnsafeRequestsBeforeAuthentication() throws Exception {
        try (var context = context()) {
            var mvc =
                    MockMvcBuilders.standaloneSetup(new SecurityBoundaryTest.EmptyController())
                            .addFilters(
                                    boundary(context),
                                    new ScalarQueryFilter(),
                                    context.getBean("springSecurityFilterChain", Filter.class))
                            .build();

            for (String path :
                    new String[] {
                        "/api/private;secret",
                        "/api/%2fprivate",
                        "/api/../private",
                        "/api/%00private"
                    }) {
                var response =
                        mvc.perform(MockMvcRequestBuilders.get(path)).andReturn().getResponse();
                assertEquals(400, response.getStatus(), path);
                assertTrue(response.getContentAsString().contains("MALFORMED_REQUEST"));
                assertFalse(response.getContentAsString().contains(path));
                assertNotNull(response.getHeader("X-Request-ID"));
            }

            assertEquals(
                    400,
                    mvc.perform(MockMvcRequestBuilders.request(HttpMethod.TRACE, "/api/private"))
                            .andReturn()
                            .getResponse()
                            .getStatus());
            assertEquals(
                    400,
                    mvc.perform(
                                    MockMvcRequestBuilders.get("/api/private")
                                            .header("X-Test", "bad\nheader"))
                            .andReturn()
                            .getResponse()
                            .getStatus());
        }
    }

    /** 인증·CSRF 실패에서는 Servlet multipart/form fallback이 열리지 않는다. */
    @Test
    void uploadDoesNotParsePartsBeforeCsrfAndAuthentication() throws Exception {
        try (var context = context()) {
            var controller =
                    new AuthController(
                            null,
                            null,
                            context.getBean(AuthCookies.class),
                            context.getBean(CookieCsrfTokenRepository.class),
                            context.getBean(JsonMapper.class));
            var mvc =
                    MockMvcBuilders.standaloneSetup(controller)
                            .addFilters(
                                    boundary(context),
                                    context.getBean("springSecurityFilterChain", Filter.class))
                            .build();
            var csrf =
                    mvc.perform(MockMvcRequestBuilders.get("/api/auth/csrf"))
                            .andReturn()
                            .getResponse();
            var cookie = csrf.getCookie("local-csrf");
            String token =
                    context.getBean(JsonMapper.class)
                            .readTree(csrf.getContentAsString())
                            .at("/data/token")
                            .asString();

            for (boolean validCsrf : new boolean[] {false, true}) {
                var builder =
                        MockMvcRequestBuilders.post("/api/files")
                                .contentType("multipart/form-data; boundary=synthetic")
                                .header("Origin", "http://localhost:3000");

                if (validCsrf) {
                    builder.cookie(cookie).header("X-CSRF-TOKEN", token);
                }

                builder.with(
                        request ->
                                new MockHttpServletRequest(request.getServletContext()) {
                                    {
                                        setMethod("POST");
                                        setRequestURI("/api/files");
                                        setServletPath("/api/files");
                                        setContentType("multipart/form-data; boundary=synthetic");
                                        addHeader("Origin", "http://localhost:3000");

                                        if (validCsrf) {
                                            setCookies(cookie);
                                            addHeader("X-CSRF-TOKEN", token);
                                        }
                                    }

                                    /** 조기 parser 실행은 시험 실패로 드러낸다. */
                                    @Override
                                    public Collection<Part> getParts() {
                                        throw new AssertionError("Premature multipart parsing");
                                    }

                                    /** CSRF form fallback도 wrapper가 차단해야 한다. */
                                    @Override
                                    public String getParameter(String name) {
                                        throw new AssertionError("Premature parameter parsing");
                                    }
                                });
                assertEquals(
                        validCsrf ? 401 : 403,
                        mvc.perform(builder).andReturn().getResponse().getStatus());
            }
        }
    }

    /** JSON 중복·미등록 권한 필드는 거부하며 정상 문자열을 삭제하거나 변조하지 않는다. */
    @Test
    void jsonRejectsDuplicateAndUnknownFieldsWithoutKeywordFiltering() {
        var configuration = new JsonConfiguration();
        var factory = JsonFactory.builder();
        configuration.strictJsonStructure().customize(factory);
        var builder = JsonMapper.builder(factory.build());
        configuration.strictJsonFields().customize(builder);
        var json = builder.build();
        assertThrows(
                RuntimeException.class,
                () -> json.readValue("{\"value\":\"a\",\"value\":\"b\"}", Input.class));
        assertThrows(
                RuntimeException.class,
                () -> json.readValue("{\"value\":\"a\",\"ownerUserId\":2}", Input.class));
        assertThrows(
                RuntimeException.class,
                () -> json.readValue("{\"value\":\"a\",\"admin\":true}", Input.class));
        String text = "한글 O'Reilly ' OR 1=1-- <script>alert(1)</script>";
        assertEquals(
                text,
                json.readValue(json.writeValueAsString(new Input(text)), Input.class).value());
        assertThrows(
                RuntimeException.class, () -> json.readTree("[".repeat(34) + "0" + "]".repeat(34)));
    }

    /** 공격 문구를 포함해도 값 자체의 의미를 바꾸지 않는 입력 fixture다. */
    record Input(String value) {}

    /** 쿠키에서 찾은 bearer가 framework의 CSRF 자동 제외 사유가 되어서는 안 된다. */
    @Test
    void cookieJwtStillRequiresCsrfForEveryUnsafeMethod() throws Exception {
        try (var context = context(SecurityBoundaryTest.JwtTestSecurity.class)) {
            var controller =
                    new AuthController(
                            null,
                            null,
                            context.getBean(AuthCookies.class),
                            context.getBean(CookieCsrfTokenRepository.class),
                            context.getBean(JsonMapper.class));
            var mvc =
                    MockMvcBuilders.standaloneSetup(
                                    controller, new SecurityBoundaryTest.ReadWriteController())
                            .addFilters(
                                    boundary(context),
                                    context.getBean("springSecurityFilterChain", Filter.class))
                            .build();
            var issued =
                    mvc.perform(MockMvcRequestBuilders.get("/api/auth/csrf"))
                            .andReturn()
                            .getResponse();
            var csrfCookie = issued.getCookie("local-csrf");
            var access = new Cookie("local-access", "synthetic-valid");
            String token =
                    context.getBean(JsonMapper.class)
                            .readTree(issued.getContentAsString())
                            .at("/data/token")
                            .asString();

            String[][] routes = {
                {"POST", "/api/files"},
                {"DELETE", "/api/files/00000000-0000-0000-0000-000000000001"},
                {"POST", "/api/auth/login"},
                {"POST", "/api/auth/refresh"},
                {"POST", "/api/auth/logout"},
                {"POST", "/api/auth/logout-all"},
                {"PUT", "/api/auth/password"},
                {"PATCH", "/api/private"}
            };

            for (String cookieValue :
                    new String[] {"synthetic-valid", "synthetic-expired", "synthetic-invalid"}) {
                for (String[] route : routes) {
                    for (int missing : new int[] {0, 1, 2}) {
                        var builder =
                                MockMvcRequestBuilders.request(
                                                HttpMethod.valueOf(route[0]), route[1])
                                        .header("Origin", "http://localhost:3000")
                                        .cookie(new Cookie("local-access", cookieValue))
                                        .contentType(
                                                route[1].equals("/api/files")
                                                        ? "multipart/form-data; boundary=synthetic"
                                                        : "application/json")
                                        .content("invalid body");
                        if (missing != 0) {
                            builder.cookie(csrfCookie);
                        }
                        if (missing == 2) {
                            builder.header("X-CSRF-TOKEN", "synthetic-wrong");
                        }
                        var response = mvc.perform(builder).andReturn().getResponse();
                        assertEquals(
                                403,
                                response.getStatus(),
                                route[0] + " " + route[1] + " cookie CSRF");
                        assertTrue(
                                response.getContentAsString()
                                        .matches("(?s).*CSRF_TOKEN_(MISSING|INVALID).*"));
                    }
                }
            }

            for (String origin : new String[] {"missing", "null", "http://localhost:3001"}) {
                var builder =
                        MockMvcRequestBuilders.post("/api/private")
                                .cookie(csrfCookie, access)
                                .header("X-CSRF-TOKEN", token);
                if (!origin.equals("missing")) {
                    builder.header("Origin", origin);
                }
                var response = mvc.perform(builder).andReturn().getResponse();
                assertEquals(403, response.getStatus());
                assertTrue(response.getContentAsString().contains("ORIGIN_REJECTED"));
            }

            for (HttpMethod method : new HttpMethod[] {HttpMethod.GET, HttpMethod.HEAD}) {
                assertEquals(
                        200,
                        mvc.perform(
                                        MockMvcRequestBuilders.request(method, "/api/private")
                                                .cookie(access))
                                .andReturn()
                                .getResponse()
                                .getStatus());
                for (String rejected : new String[] {"synthetic-expired", "synthetic-invalid"}) {
                    var response =
                            mvc.perform(
                                            MockMvcRequestBuilders.request(method, "/api/private")
                                                    .cookie(new Cookie("local-access", rejected)))
                                    .andReturn()
                                    .getResponse();
                    assertEquals(401, response.getStatus());
                    if (method == HttpMethod.HEAD) {
                        assertEquals(0, response.getContentAsByteArray().length);
                    }
                }
            }

            assertEquals(
                    200,
                    mvc.perform(
                                    MockMvcRequestBuilders.post("/api/private")
                                            .cookie(csrfCookie, access)
                                            .header("Origin", "http://localhost:3000")
                                            .header("X-CSRF-TOKEN", token))
                            .andReturn()
                            .getResponse()
                            .getStatus());
        }
    }

    /** 선택 prelogin chain은 API 쿠키를 인증이나 CSRF 제외 사유로 사용하지 않는다. */
    @Test
    void optionalPreloginChainKeepsItsOwnCsrfBoundary() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.setEnvironment(
                    TestPolicies.environment()
                            .withProperty("app.auth.prelogin-session.enabled", "true"));
            context.register(
                    SecurityBoundaryTest.JwtTestSecurity.class,
                    SecurityConfiguration.class,
                    ExternalAuthenticationConfiguration.class);
            context.refresh();
            var controller =
                    new AuthController(
                            null,
                            null,
                            context.getBean(AuthCookies.class),
                            context.getBean(CookieCsrfTokenRepository.class),
                            context.getBean(JsonMapper.class));
            var mvc =
                    MockMvcBuilders.standaloneSetup(controller)
                            .addFilters(
                                    boundary(context),
                                    context.getBean("springSecurityFilterChain", Filter.class))
                            .build();
            var issued =
                    mvc.perform(MockMvcRequestBuilders.get("/api/auth/csrf"))
                            .andReturn()
                            .getResponse();
            var cookie = issued.getCookie("local-csrf");
            String token =
                    context.getBean(JsonMapper.class)
                            .readTree(issued.getContentAsString())
                            .at("/data/token")
                            .asString();
            var csrf =
                    context
                            .getBean("externalAuthentication", SecurityFilterChain.class)
                            .getFilters()
                            .stream()
                            .filter(CsrfFilter.class::isInstance)
                            .map(CsrfFilter.class::cast)
                            .findFirst()
                            .orElseThrow();
            var calls = new AtomicInteger();

            for (String value :
                    new String[] {"synthetic-valid", "synthetic-expired", "synthetic-invalid"}) {
                var request = new MockHttpServletRequest("POST", "/auth/external/callback");
                request.setCookies(cookie, new Cookie("local-access", value));
                var response = new MockHttpServletResponse();
                csrf.doFilter(request, response, (a, b) -> calls.incrementAndGet());
                assertEquals(403, response.getStatus());
            }

            assertEquals(0, calls.get());
            var request = new MockHttpServletRequest("POST", "/auth/external/callback");
            request.setCookies(cookie);
            request.addHeader("X-CSRF-TOKEN", token);
            csrf.doFilter(
                    request, new MockHttpServletResponse(), (a, b) -> calls.incrementAndGet());
            assertEquals(1, calls.get());
            assertEquals(
                    401,
                    mvc.perform(
                                    MockMvcRequestBuilders.post("/auth/external/callback")
                                            .cookie(
                                                    cookie,
                                                    new Cookie("local-access", "synthetic-valid"))
                                            .header("X-CSRF-TOKEN", token))
                            .andReturn()
                            .getResponse()
                            .getStatus());
        }
    }

    /** 제품 인증 구성을 DB 없는 고정 의존성에 연결한다. */
    private AnnotationConfigWebApplicationContext context() {
        return context(SecurityBoundaryTest.TestSecurity.class);
    }

    /** 실제 chain의 JWT 성공/익명 의존성을 선택하며 외부 저장소는 만들지 않는다. */
    private AnnotationConfigWebApplicationContext context(Class<?> security) {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.setEnvironment(TestPolicies.environment());
        context.register(security, SecurityConfiguration.class);
        context.refresh();

        return context;
    }

    /** 제품 최외곽 오류 필터를 그대로 사용한다. */
    private RequestBoundaryFilter boundary(AnnotationConfigWebApplicationContext context) {
        return new RequestBoundaryFilter(
                context.getBean(ProblemResponses.class),
                TestPolicies.environment(),
                context.getBean(DenialAuditService.class),
                context.getBean(AuthStateStore.class),
                context.getBean(Clock.class));
    }
}
