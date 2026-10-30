package com.example.starter.auth.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.TestPolicies;
import com.example.starter.common.config.SessionConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.session.jdbc.autoconfigure.JdbcSessionAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;

/** off 조건을 실제 bean parser에서 검사하며 외부 자동 설정/접속은 실행하지 않는다. */
class DisabledRepositoryTest {
    /** 잘못된 제외 클래스명이 실제 Boot JDBC Session 자동 설정을 조용히 허용하지 않게 한다. */
    @Test
    void configuredExclusionsResolveInstalledClassesAndDisableJdbcSessionAutoConfiguration()
            throws Exception {
        var exclusions =
                Binder.get(TestPolicies.environment())
                        .bind("spring.autoconfigure.exclude", Bindable.listOf(String.class))
                        .get();
        assertTrue(exclusions.contains(JdbcSessionAutoConfiguration.class.getName()));
        for (String exclusion : exclusions) {
            Class.forName(exclusion, false, getClass().getClassLoader());
        }
    }

    /** nested 설정이 독립 component로 스캔되어도 off 세션과 JDBC Redis가 생기지 않는다. */
    @Test
    void jdbcWithPreloginDisabledHasNoRedisOrSessionBeans() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(
                    TestPolicies.environment().withProperty("app.auth.state-store", "jdbc"));
            context.register(
                    RedisStateConfiguration.class,
                    SessionConfiguration.class,
                    PreloginSessionConfiguration.class,
                    PreloginSessionConfiguration.RedisPrelogin.class,
                    PreloginSessionConfiguration.JdbcPrelogin.class);
            context.refresh();
            assertTrue(context.getBeansOfType(RedisConnectionFactory.class).isEmpty());
            assertTrue(context.getBeansOfType(SessionRepository.class).isEmpty());
            assertTrue(context.getBeansOfType(SessionRepositoryFilter.class).isEmpty());
        }
    }
}
