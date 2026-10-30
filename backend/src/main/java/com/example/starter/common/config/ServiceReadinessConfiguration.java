package com.example.starter.common.config;

import com.example.starter.audit.service.LoginReferences;
import com.example.starter.auth.config.SigningKeys;
import com.example.starter.auth.store.AuthStateStore;
import com.example.starter.auth.store.TokenSecrets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import tools.jackson.databind.json.JsonMapper;

/** 선택한 저장소·동일 키/정책·조건부 세션의 준비 상태만 확인한다. */
@Configuration(proxyBeanMethods = false)
public class ServiceReadinessConfiguration {
    /** 실제 기동은 정책/probe 쓰기를 포함하므로 AI 실행은 별도 승인 대상이다. */
    @Bean
    SmartInitializingSingleton serviceReadiness(
            DataSource source,
            AuthStateStore states,
            SigningKeys keys,
            LoginReferences references,
            AppProperties properties,
            JsonMapper json,
            ObjectProvider<SessionRepository<? extends Session>> sessions) {
        return () -> {
            try (Connection connection = source.getConnection()) {
                connection.setReadOnly(true);

                if (!connection.isValid(3)) {
                    throw new IllegalStateException("Database not ready");
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Database readiness failed", exception);
            }

            try {
                var policy = properties.auth();
                String publicKeys =
                        keys.publicKeys().getKeys().stream()
                                .map(key -> key.toJSONString())
                                .sorted()
                                .collect(Collectors.joining());
                String digest =
                        TokenSecrets.hash(
                                json.writeValueAsString(
                                        List.of(
                                                policy.namespace(),
                                                policy.namespaceGeneration(),
                                                policy.stateStore().name(),
                                                policy.concurrentLogin().name(),
                                                policy.jwt().issuer(),
                                                policy.jwt().audience(),
                                                policy.accessTtl().toString(),
                                                policy.idleTimeout().toString(),
                                                policy.absoluteTimeout().toString(),
                                                policy.jwt().clockSkew().toString(),
                                                keys.active().getKeyID(),
                                                publicKeys,
                                                references.policyFingerprint(),
                                                policy.failedLogin().window().toString(),
                                                policy.failedLogin().maxAttempts(),
                                                policy.failedLogin().lockDuration().toString(),
                                                policy.failedLogin().ipMaxAttempts(),
                                                policy.failedLogin().ipWindow().toString(),
                                                policy.preloginSession().enabled(),
                                                policy.preloginSession().timeout().toString(),
                                                policy.preloginSession().namespace(),
                                                policy.csrf().maxAge().toString())));
                states.verifyPolicy(digest);

                if (properties.auth().preloginSession().enabled()) {
                    SessionRepository<? extends Session> repository = sessions.getIfUnique();

                    if (repository == null) {
                        throw new IllegalStateException("Session repository required");
                    }

                    probe(repository);
                }

                if (properties.logging().fileEnabled()) {
                    Path directory =
                            Path.of(
                                    properties.logging().directory(),
                                    policy.namespace().split(":")[1],
                                    properties.serverId());
                    Files.createDirectories(directory);
                    Path probe = Files.createTempFile(directory, "readiness-", ".tmp");
                    Files.delete(probe);
                }
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "Authentication or log readiness failed", exception);
            }
        };
    }

    /** 허용된 속성의 짧은 save/read/delete probe는 세션 옵션을 켠 경우만 실행한다. */
    private static <S extends Session> void probe(SessionRepository<S> repository) {
        S session = repository.createSession();
        session.setMaxInactiveInterval(Duration.ofSeconds(30));
        session.setAttribute("readiness", "probe-v2");

        try {
            repository.save(session);
            S restored = repository.findById(session.getId());

            if (restored == null || !"probe-v2".equals(restored.getAttribute("readiness"))) {
                throw new IllegalStateException("Session JSON probe failed");
            }
        } finally {
            repository.deleteById(session.getId());
        }
    }
}
