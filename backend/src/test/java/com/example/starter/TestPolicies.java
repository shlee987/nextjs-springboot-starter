package com.example.starter;

import com.example.starter.audit.service.LoginReferences;
import com.example.starter.common.config.AppProperties;
import com.example.starter.common.config.EnvironmentGuard;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

/** 실제 YAML을 사용하는 외부 연결 없는 테스트 설정이다. */
public final class TestPolicies {
    /** 테스트 설정 도구의 인스턴스 생성을 막는다. */
    private TestPolicies() {}

    /** local 기본 설정을 읽기만 하고 App/DB/키 생성은 실행하지 않는다. */
    public static MockEnvironment environment() {
        try {
            var environment = new MockEnvironment();
            environment.setActiveProfiles("local");
            environment.withProperty("spring.profiles.active", "local");
            var loader = new YamlPropertySourceLoader();
            environment
                    .getPropertySources()
                    .addLast(
                            loader.load("local", new ClassPathResource("application-local.yml"))
                                    .getFirst());
            environment
                    .getPropertySources()
                    .addLast(
                            loader.load("common", new ClassPathResource("application.yml"))
                                    .getFirst());
            new EnvironmentGuard().postProcessEnvironment(environment, new SpringApplication());

            return environment;
        } catch (Exception exception) {
            throw new IllegalStateException("Test configuration failed", exception);
        }
    }

    /** DB/서명 키와 무관한 합성 HMAC 시험 key다. */
    public static LoginReferences references() {
        return new LoginReferences(new byte[32]);
    }

    /** 기본 정책을 typed 설정으로 binding한다. */
    public static AppProperties local() {
        return Binder.get(environment()).bind("app", AppProperties.class).get();
    }
}
