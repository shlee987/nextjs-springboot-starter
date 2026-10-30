package com.example.starter.auth.config;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 저장 hash 식별자와 확정 Argon2id 비용을 고정한다. */
@Configuration(proxyBeanMethods = false)
public class PasswordConfiguration {
    /** 다른 알고리즘 fallback 없이 {argon2} 형식으로 저장한다. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder(
                "argon2", Map.of("argon2", new Argon2PasswordEncoder(16, 32, 1, 19456, 2)));
    }
}
