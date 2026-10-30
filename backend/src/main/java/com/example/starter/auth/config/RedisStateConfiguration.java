package com.example.starter.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** JDBC 모드에서는 연결 factory와 template도 만들지 않는다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.auth.state-store", havingValue = "redis", matchIfMissing = true)
@Import(DataRedisAutoConfiguration.class)
public class RedisStateConfiguration {}
