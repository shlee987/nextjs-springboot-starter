package com.example.starter.audit.service;

import com.example.starter.auth.config.SigningKeys;
import com.example.starter.common.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 공유 감사 키는 외부 파일, local만 개발 서명 키에서 별도 label로 파생한다. */
@Configuration(proxyBeanMethods = false)
public class AuditKeyConfiguration {
    /** 개발 키를 외부 감사 자격의 기본값으로 사용하지 않는다. */
    @Bean
    @ConditionalOnProperty(name = "app.bootstrap.local-data-enabled", havingValue = "true")
    LoginReferences localLoginReferences(SigningKeys keys) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(
                    "starter-local-audit-login-reference-v1\0".getBytes(StandardCharsets.UTF_8));

            return new LoginReferences(digest.digest(keys.active().toRSAPrivateKey().getEncoded()));
        } catch (Exception exception) {
            throw new IllegalStateException("Local audit key configuration failed");
        }
    }

    /** 공유 환경은 최소32byte의 별도 운영 secret 파일을 요구한다. */
    @Bean
    @ConditionalOnProperty(
            name = "app.bootstrap.local-data-enabled",
            havingValue = "false",
            matchIfMissing = true)
    LoginReferences externalLoginReferences(AppProperties properties) {
        try {
            Path path = Path.of(properties.audit().loginReferenceKeyLocation());

            if (Files.size(path) > 4096) {
                throw new IllegalArgumentException("Invalid audit secret size");
            }

            return new LoginReferences(Files.readAllBytes(path));
        } catch (Exception exception) {
            throw new IllegalStateException("External audit key configuration failed");
        }
    }
}
