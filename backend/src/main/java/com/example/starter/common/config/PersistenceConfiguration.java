package com.example.starter.common.config;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcTransactionManager;

/** Boot의 단일 DataSource를 JDBC와 MyBatis 업무 트랜잭션에서 공유한다. */
@Configuration(proxyBeanMethods = false)
public class PersistenceConfiguration {
    /** MyBatis 자동 설정과 동일한 pool의 연결을 서비스 트랜잭션에 연결한다. */
    @Bean
    JdbcTransactionManager transactionManager(DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }
}
