package com.example.starter.common.config;

import com.example.starter.audit.service.DenialAuditService;
import com.example.starter.auth.config.AuthAuthenticationConverter;
import com.example.starter.auth.model.AuthError;
import com.example.starter.auth.store.AuthStateStore;
import com.example.starter.auth.web.AuthCookies;
import com.example.starter.auth.web.OriginFilter;
import com.example.starter.auth.web.RequestRateFilter;
import com.example.starter.common.error.ErrorCode;
import com.example.starter.common.web.ApiError;
import com.example.starter.common.web.ProblemResponses;
import com.example.starter.file.service.UploadAdmission;
import com.example.starter.file.web.UploadGateFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.security.web.firewall.FirewalledRequest;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** API JWT chain과 공개 인증 chain을 분리해 만료 access가 갱신을 방해하지 않게 한다. */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    /** 기본 StrictHttpFirewall을 완화하지 않고 거부도 공통 오류로 반환한다. */
    @Bean
    WebSecurityCustomizer strictFirewall(ProblemResponses problems) {
        return web ->
                web.httpFirewall(
                                new StrictHttpFirewall() {
                                    /** 애플리케이션이 사용하지 않는 헤더도 기본 firewall 검사를 통과해야 한다. */
                                    @Override
                                    public FirewalledRequest getFirewalledRequest(
                                            HttpServletRequest request) {
                                        var checked = super.getFirewalledRequest(request);
                                        var names = checked.getHeaderNames();
                                        while (names.hasMoreElements()) {
                                            var values = checked.getHeaders(names.nextElement());
                                            while (values.hasMoreElements()) {
                                                values.nextElement();
                                            }
                                        }
                                        return checked;
                                    }
                                })
                        .requestRejectedHandler(
                                (request, response, failure) ->
                                        problems.write(
                                                ApiError.MALFORMED_REQUEST,
                                                request,
                                                response,
                                                failure));
    }

    /** 필터와 서비스가 같은 처리 슬롯을 사용한다. */
    @Bean
    UploadAdmission uploadAdmission() {
        return new UploadAdmission();
    }

    /** 세션 없이 HttpOnly cookie와 XOR 공개 token을 사용하는 표준 repository다. */
    @Bean
    CookieCsrfTokenRepository csrfRepository(AuthCookies cookies, AppProperties properties) {
        var repository = new CookieCsrfTokenRepository();
        repository.setCookieName(cookies.name("csrf"));
        repository.setHeaderName("X-CSRF-TOKEN");
        repository.setCookieCustomizer(
                cookie ->
                        cookie.httpOnly(true)
                                .secure(cookies.secure())
                                .path("/")
                                .sameSite("Lax")
                                .maxAge(properties.auth().csrf().maxAge()));

        return repository;
    }

    /** 정확한 메서드/경로만 공개하고 JWT filter는 이 chain에 등록하지 않는다. */
    @Bean
    @Order(1)
    SecurityFilterChain publicAuthentication(
            HttpSecurity http,
            CookieCsrfTokenRepository csrf,
            AppProperties properties,
            ProblemResponses problems,
            DenialAuditService audits,
            AuthStateStore states,
            Clock clock)
            throws Exception {
        var paths = PathPatternRequestMatcher.withDefaults();
        RequestMatcher endpoints =
                new OrRequestMatcher(
                        paths.matcher(HttpMethod.GET, "/api/auth/csrf"),
                        paths.matcher(HttpMethod.POST, "/api/auth/login"),
                        paths.matcher(HttpMethod.POST, "/api/auth/refresh"),
                        paths.matcher(HttpMethod.POST, "/api/auth/logout"));
        http.securityMatcher(endpoints);
        common(http, csrf, properties, problems, audits);
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        http.addFilterBefore(new RequestRateFilter(states, clock), OriginFilter.class);

        return http.build();
    }

    /** API는 request SecurityContext만 사용하며 HttpSession을 인증 수단으로 읽지 않는다. */
    @Bean
    @Order(2)
    SecurityFilterChain protectedApi(
            HttpSecurity http,
            CookieCsrfTokenRepository csrf,
            AppProperties properties,
            ProblemResponses problems,
            DenialAuditService audits,
            AuthCookies cookies,
            JwtDecoder decoder,
            AuthStateStore states,
            Clock clock,
            AuthAuthenticationConverter converter,
            UploadAdmission admission)
            throws Exception {
        http.securityMatcher("/api/**");
        common(http, csrf, properties, problems, audits);
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());
        http.oauth2ResourceServer(
                resource -> {
                    resource.bearerTokenResolver(request -> cookies.read(request, "access"));
                    resource.jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter));
                    resource.authenticationEntryPoint(
                            (request, response, failure) -> {
                                RequestRateFilter.limitAnonymous(states, clock, request);
                                ErrorCode error = ApiError.UNAUTHENTICATED;

                                if (failure.getCause() instanceof JwtValidationException invalid
                                        && invalid.getErrors().stream()
                                                .anyMatch(
                                                        item ->
                                                                "TOKEN_EXPIRED"
                                                                        .equals(
                                                                                item
                                                                                        .getErrorCode()))) {
                                    error = AuthError.TOKEN_EXPIRED;
                                }

                                audits.denied(request, error.name());
                                problems.write(error, request, response, failure);
                            });
                });
        http.addFilterAfter(
                new RequestRateFilter(states, clock), BearerTokenAuthenticationFilter.class);
        http.addFilterAfter(
                new UploadGateFilter(admission, states, clock), AuthorizationFilter.class);

        return http.build();
    }

    /** 미등록 경로는 기본 거부하고 error dispatch만 공통 오류 출력을 허용한다. */
    @Bean
    @Order(4)
    SecurityFilterChain fallback(
            HttpSecurity http,
            AppProperties properties,
            ProblemResponses problems,
            DenialAuditService audits,
            CookieCsrfTokenRepository csrf,
            Environment environment)
            throws Exception {
        common(http, csrf, properties, problems, audits);
        http.authorizeHttpRequests(
                authorize -> {
                    authorize
                            .dispatcherTypeMatchers(DispatcherType.ERROR)
                            .permitAll()
                            .requestMatchers("/error")
                            .permitAll();

                    if (environment.getProperty(
                            "springdoc.api-docs.enabled", Boolean.class, false)) {
                        authorize
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/v3/api-docs/**",
                                        "/swagger-ui/**",
                                        "/swagger-ui.html")
                                .permitAll();
                    }

                    authorize.anyRequest().denyAll();
                });

        return http.build();
    }

    /** API 기본 헤더·Origin·CSRF·오류·비세션 정책을 같은 표준 확장점에 적용한다. */
    private void common(
            HttpSecurity http,
            CookieCsrfTokenRepository csrf,
            AppProperties properties,
            ProblemResponses problems,
            DenialAuditService audits)
            throws Exception {
        // JWT의 매 요청 인증은 로그인 이벤트가 아니다. CSRF 교체는 login/logout Controller가 소유한다.
        http.csrf(
                config ->
                        config.csrfTokenRepository(csrf)
                                .sessionAuthenticationStrategy(
                                        new NullAuthenticatedSessionStrategy())
                                .withObjectPostProcessor(
                                        new ObjectPostProcessor<CsrfFilter>() {
                                            /**
                                             * 쿠키 bearer를 감지한 resource-server의 자동 CSRF 제외를 사용하지 않는다.
                                             */
                                            @Override
                                            public <O extends CsrfFilter> O postProcess(O filter) {
                                                filter.setRequireCsrfProtectionMatcher(
                                                        CsrfFilter.DEFAULT_CSRF_MATCHER);

                                                return filter;
                                            }
                                        }));
        http.addFilterBefore(new OriginFilter(properties.publicOrigin()), CsrfFilter.class);
        http.securityContext(
                context -> context.securityContextRepository(new NullSecurityContextRepository()));
        http.sessionManagement(
                session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.requestCache(cache -> cache.requestCache(new NullRequestCache()));
        http.formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());
        http.headers(headers -> headers.httpStrictTransportSecurity(hsts -> hsts.disable()));
        http.exceptionHandling(
                errors ->
                        errors.authenticationEntryPoint(
                                        (request, response, failure) -> {
                                            audits.denied(request, "UNAUTHENTICATED");
                                            problems.write(
                                                    ApiError.UNAUTHENTICATED,
                                                    request,
                                                    response,
                                                    failure);
                                        })
                                .accessDeniedHandler(
                                        (request, response, failure) -> {
                                            ErrorCode error =
                                                    failure instanceof MissingCsrfTokenException
                                                            ? ApiError.CSRF_TOKEN_MISSING
                                                            : failure
                                                                            instanceof
                                                                            InvalidCsrfTokenException
                                                                    ? ApiError.CSRF_TOKEN_INVALID
                                                                    : ApiError.ACCESS_DENIED;
                                            audits.denied(request, error.name());
                                            problems.write(error, request, response, failure);
                                        }));
    }
}
