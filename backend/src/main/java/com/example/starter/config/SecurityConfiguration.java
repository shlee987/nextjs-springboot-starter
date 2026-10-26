package com.example.starter.config;

import com.example.starter.web.AbsoluteSessionExpiryFilter;
import com.example.starter.web.ApiError;
import com.example.starter.web.ProblemResponses;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.security.web.savedrequest.NullRequestCache;

/** 인증 기능이 확정되기 전에는 기본 거부와 세션 CSRF 보호만 제공한다. */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    /** 공개 인증 경로 없이 CSRF·세션 ID 교체·고정 오류 경계를 적용한다. */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemResponses problems)
            throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().denyAll());
        http.csrf(csrf -> csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository()));
        http.addFilterBefore(new AbsoluteSessionExpiryFilter(),
                SecurityContextHolderFilter.class);
        http.requestCache(cache -> cache.requestCache(new NullRequestCache()));
        http.sessionManagement(session -> session
                .sessionFixation(fixation -> fixation.changeSessionId()));
        http.formLogin(form -> form.disable());
        http.httpBasic(basic -> basic.disable());
        http.logout(logout -> logout.disable());
        // HSTS는 TLS 종료 프록시가 소유하며 앱에서 중복 생성하지 않는다.
        http.headers(headers -> headers.httpStrictTransportSecurity(hsts -> hsts.disable()));
        http.exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                        problems.write(ApiError.UNAUTHENTICATED, request, response, exception))
                .accessDeniedHandler((request, response, exception) -> {
                    ApiError error = exception instanceof MissingCsrfTokenException
                            ? ApiError.CSRF_TOKEN_MISSING
                            : exception instanceof InvalidCsrfTokenException
                                    ? ApiError.CSRF_TOKEN_INVALID : ApiError.ACCESS_DENIED;
                    problems.write(error, request, response, exception);
                }));

        return http.build();
    }
}
