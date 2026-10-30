package com.example.starter.auth.config;

import com.example.starter.common.web.ApiError;
import com.example.starter.common.web.ProblemResponses;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

/** 실제 provider 미도입 상태의 별도 IF_REQUIRED 경계다. 임의 callback은 허용하지 않는다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.auth.prelogin-session.enabled", havingValue = "true")
public class ExternalAuthenticationConfiguration {
    /** 외부 인증은 옵션 세션을 쓸 수 있으나 API JWT chain과 인증을 공유하지 않는다. */
    @Bean
    @Order(3)
    SecurityFilterChain externalAuthentication(
            HttpSecurity http, ProblemResponses problems, CookieCsrfTokenRepository csrf)
            throws Exception {
        http.securityMatcher("/auth/external/**");
        http.sessionManagement(
                session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
        http.requestCache(cache -> cache.requestCache(new NullRequestCache()));
        http.csrf(config -> config.csrfTokenRepository(csrf));
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().denyAll());
        http.formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());
        http.exceptionHandling(
                errors ->
                        errors.authenticationEntryPoint(
                                        (request, response, failure) ->
                                                problems.write(
                                                        ApiError.UNAUTHENTICATED,
                                                        request,
                                                        response,
                                                        failure))
                                .accessDeniedHandler(
                                        (request, response, failure) ->
                                                problems.write(
                                                        ApiError.ACCESS_DENIED,
                                                        request,
                                                        response,
                                                        failure)));

        return http.build();
    }
}
