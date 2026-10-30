package com.example.starter.auth.config;

import com.example.starter.common.config.AppProperties;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Spring/Nimbus의 JWT 서명과 고정 RS256·at+jwt 검증을 구성한다. */
@Configuration(proxyBeanMethods = false)
public class JwtConfiguration {
    /** encoder에만 active private JWK를 전달한다. */
    @Bean
    JwtEncoder jwtEncoder(SigningKeys keys) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(keys.active())));
    }

    /** 신뢰한 로컬 공개키 목록으로만 검증하고 원격 header URL은 사용하지 않는다. */
    @Bean
    JwtDecoder jwtDecoder(SigningKeys keys, AppProperties properties, Clock clock) {
        var processor = new DefaultJWTProcessor<SecurityContext>();
        processor.setJWSTypeVerifier(
                new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType("at+jwt")));
        processor.setJWSKeySelector(
                new JWSVerificationKeySelector<>(
                        JWSAlgorithm.RS256, new ImmutableJWKSet<>(keys.publicKeys())));
        // 시간·필수 claim은 아래 주입 Clock validator에서 한 번만 검증한다.
        processor.setJWTClaimsSetVerifier((claims, context) -> {});
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        new JwtIssuerValidator(properties.auth().jwt().issuer()),
                        requiredClaims(properties, clock)));

        return decoder;
    }

    /** 필수 식별자·프로토콜 NumericDate·만료/nbf·audience를 검증한다. */
    static OAuth2TokenValidator<Jwt> requiredClaims(AppProperties properties, Clock clock) {
        return jwt -> {
            try {
                var settings = properties.auth().jwt();
                Instant now = clock.instant();
                Instant expires = jwt.getExpiresAt();
                Instant issued = jwt.getIssuedAt();
                Instant notBefore = jwt.getNotBefore();
                Object authTime = jwt.getClaim("auth_time");
                String kid = (String) jwt.getHeaders().get("kid");

                if (expires == null
                        || issued == null
                        || notBefore == null
                        || !(authTime instanceof Number)
                        || kid == null
                        || kid.isBlank()
                        || !"RS256".equals(jwt.getHeaders().get("alg"))
                        || !"at+jwt".equals(jwt.getHeaders().get("typ"))
                        || !jwt.getAudience().contains(settings.audience())
                        || !properties
                                .auth()
                                .namespaceGeneration()
                                .equals(jwt.getClaimAsString("asg"))
                        || !jwt.getSubject().matches("[1-9][0-9]{0,18}")
                        || Long.parseLong(jwt.getSubject()) <= 0
                        || issued.isAfter(now.plus(settings.clockSkew()))
                        || notBefore.isAfter(now.plus(settings.clockSkew()))
                        || !expires.isAfter(issued)
                        || expires.isAfter(issued.plus(properties.auth().accessTtl()))
                        || !UUID.fromString(jwt.getClaimAsString("sid"))
                                .toString()
                                .equals(jwt.getClaimAsString("sid"))
                        || !UUID.fromString(jwt.getId()).toString().equals(jwt.getId())
                        || ((Number) authTime).longValue() > issued.getEpochSecond()) {
                    return invalid("invalid_token");
                }

                if (!now.minus(settings.clockSkew()).isBefore(expires)) {
                    return invalid("TOKEN_EXPIRED");
                }

                return OAuth2TokenValidatorResult.success();
            } catch (RuntimeException exception) {
                return invalid("invalid_token");
            }
        };
    }

    /** 예외 원문 없이 고정 실패 사유만 보안 entrypoint에 제공한다. */
    private static OAuth2TokenValidatorResult invalid(String code) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(code));
    }
}
