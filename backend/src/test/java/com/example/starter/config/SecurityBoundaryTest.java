package com.example.starter.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.web.ProblemResponses;
import com.example.starter.web.RequestBoundaryFilter;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

/** 실제 Security 체인의 기본 거부와 CSRF 실패를 외부 저장소 없이 검사한다. */
class SecurityBoundaryTest {
    /** 익명 GET은 401, CSRF 없는 변경은 403이며 HTML 로그인·기본 계정이 없다. */
    @Test
    void securityDeniesAnonymousRequestsAndMissingCsrf() throws Exception {
        try (AnnotationConfigWebApplicationContext context =
                new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(TestSecurity.class, SecurityConfiguration.class);
            context.refresh();
            ProblemResponses problems = context.getBean(ProblemResponses.class);
            MockMvc mvc = MockMvcBuilders.standaloneSetup(new EmptyController())
                    .addFilters(new RequestBoundaryFilter(problems),
                            context.getBean("springSecurityFilterChain", Filter.class)).build();
            var denied = mvc.perform(MockMvcRequestBuilders.get("/api/private").accept("text/html"))
                    .andReturn().getResponse();
            assertEquals(401, denied.getStatus());
            assertTrue(denied.getContentAsString().contains("UNAUTHENTICATED"));
            assertFalse(denied.getContentAsString().contains("<html"));
            var csrf = mvc.perform(MockMvcRequestBuilders.post("/api/private"))
                    .andReturn().getResponse();
            assertEquals(403, csrf.getStatus());
            assertTrue(csrf.getContentAsString().contains("CSRF_TOKEN_MISSING"));
            assertEquals("nosniff", csrf.getHeader("X-Content-Type-Options"));
            MockHttpSession session = new MockHttpSession();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setSession(session);
            HttpSessionCsrfTokenRepository csrfRepository = new HttpSessionCsrfTokenRepository();
            csrfRepository.saveToken(csrfRepository.generateToken(request), request,
                    new MockHttpServletResponse());
            var invalidCsrf = mvc.perform(MockMvcRequestBuilders.post("/api/private")
                    .session(session).header("X-CSRF-TOKEN", "synthetic-invalid"))
                    .andReturn().getResponse();
            assertEquals(403, invalidCsrf.getStatus());
            assertTrue(invalidCsrf.getContentAsString().contains("CSRF_TOKEN_INVALID"));
            session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                    UsernamePasswordAuthenticationToken.authenticated("synthetic-subject", null,
                            java.util.List.of())));
            var forbidden = mvc.perform(MockMvcRequestBuilders.get("/api/private").session(session))
                    .andReturn().getResponse();
            assertEquals(403, forbidden.getStatus());
            assertTrue(forbidden.getContentAsString().contains("ACCESS_DENIED"));
            assertTrue(context.getBeansOfType(UserDetailsService.class)
                    .isEmpty());
        }
    }

    /** DB·Redis 자동 설정 없이 실제 Security 구성을 불러오는 격리 컨텍스트다. */
    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestSecurity {
        /** 공통 오류 직렬화의 제품 경계를 재사용한다. */
        @Bean
        ProblemResponses problems() {
            return new ProblemResponses(JsonMapper.builder().build());
        }
    }

    /** 테스트 MVC를 초기화하는 빈 라우트 소유자다. 업무 endpoint는 만들지 않는다. */
    @org.springframework.web.bind.annotation.RestController
    static class EmptyController {
    }
}
