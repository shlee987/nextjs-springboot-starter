package com.example.starter.common.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** 공통 시계·타입 설정·명시적 업무 트랜잭션과 메서드 권한을 제공한다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppProperties.class)
@EnableTransactionManagement(order = 500)
@EnableMethodSecurity
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class FoundationConfiguration {
    /** 테스트에서 대체할 수 있는 UTC 시계를 제공한다. */
    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }
}
