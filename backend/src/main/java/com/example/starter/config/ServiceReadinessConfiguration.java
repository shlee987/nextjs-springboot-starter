package com.example.starter.config;

import java.sql.Connection;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;

/** 웹 서버가 요청을 받기 전에 필수 저장소의 접속과 세션 저장 계약을 확인한다. */
@Configuration(proxyBeanMethods = false)
public class ServiceReadinessConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceReadinessConfiguration.class);

    /** DB 읽기 전용 연결과 단기 Redis probe 세션의 저장·복원·삭제를 수행한다.
     * 앱 기동은 Redis 변경을 수반하므로 AI 실행 시 DB 접근 승인 범위를 적용한다.
     */
    @Bean
    SmartInitializingSingleton serviceReadiness(DataSource dataSource,
            RedisIndexedSessionRepository sessions) {
        return () -> {
            // 연결 오류의 원문은 시작 실패 메시지에 포함하지 않는다.
            try (Connection connection = dataSource.getConnection()) {
                connection.setReadOnly(true);

                if (!connection.isValid(3)) {
                    throw new IllegalStateException("Database readiness check failed");
                }
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "Database readiness check failed; verify external configuration");
            }

            LOG.info("service_readiness database=ready readOnly=true");
            var probe = sessions.createSession();
            probe.setMaxInactiveInterval(java.time.Duration.ofSeconds(30));
            probe.setAttribute("readiness", "probe-v1");

            try {
                sessions.save(probe);
                var restored = sessions.findById(probe.getId());

                if (restored == null || !"probe-v1".equals(restored.getAttribute("readiness"))) {
                    throw new IllegalStateException("Session readiness check failed");
                }

                sessions.deleteById(probe.getId());
                LOG.info("service_readiness session=ready repository=indexed "
                        + "probe=save-read-delete");
            } catch (Exception exception) {
                // 부분 실패한 probe는 30초 유휴 수명을 사용하며 로컬 저장소로 대체하지 않는다.
                throw new IllegalStateException(
                        "Session readiness check failed; verify external configuration");
            }
        };
    }
}
