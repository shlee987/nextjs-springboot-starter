package com.example.starter.auth.config;

import com.example.starter.common.config.AppProperties;
import com.example.starter.common.config.SessionConfiguration.SessionJsonCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

/** prelogin off면 두 저장소와 관련 세션 filter를 모두 등록하지 않는다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.auth.prelogin-session.enabled", havingValue = "true")
public class PreloginSessionConfiguration {
    /** Redis 세션 listener는 실제 활성 모드에서만 만든다. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            name = "app.auth.state-store",
            havingValue = "redis",
            matchIfMissing = true)
    @ConditionalOnExpression("${app.auth.prelogin-session.enabled:false}")
    @EnableRedisIndexedHttpSession(
            redisNamespace = "${app.auth.prelogin-session.namespace}",
            maxInactiveIntervalInSeconds = 600)
    public static class RedisPrelogin {
        /** Redis 전용 adapter는 실제 Redis prelogin 모드에만 등록한다. */
        @Bean
        RedisSerializer<Object> springSessionDefaultRedisSerializer(SessionJsonCodec codec) {
            return new RedisSerializer<>() {
                /** 허용한 JSON 타입만 Redis 속성에 저장한다. */
                @Override
                public byte[] serialize(Object value) {
                    return codec.serialize(value);
                }

                /** 동일 codec으로 복원하며 Java native 직렬화는 사용하지 않는다. */
                @Override
                public Object deserialize(byte[] bytes) {
                    return codec.deserialize(bytes);
                }
            };
        }

        /** 서버 Redis CONFIG 명령은 앱에서 변경하지 않는다. */
        @Bean
        ConfigureRedisAction configureRedisAction() {
            return ConfigureRedisAction.NO_OP;
        }

        /** typed timeout으로 모든 노드의 생성 수명을 통일한다. */
        @Bean
        SessionRepositoryCustomizer<RedisIndexedSessionRepository> redisSessionPolicy(
                AppProperties properties) {
            return repository ->
                    repository.setDefaultMaxInactiveInterval(
                            properties.auth().preloginSession().timeout());
        }
    }

    /** JDBC 모드는 Redis 접속 없이 독립 schema만 사용한다. */
    @Configuration(proxyBeanMethods = false)
    @DependsOn("foundationMigrations")
    @ConditionalOnProperty(name = "app.auth.state-store", havingValue = "jdbc")
    @ConditionalOnExpression("${app.auth.prelogin-session.enabled:false}")
    @EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 600, cleanupCron = "0 * * * * *")
    public static class JdbcPrelogin {
        /** bytea를 명시 JSON codec으로 왕복하여 native Java 직렬화를 사용하지 않는다. */
        @Bean("springSessionConversionService")
        GenericConversionService sessionConversion(SessionJsonCodec codec) {
            var conversion = new GenericConversionService();
            conversion.addConverter(Object.class, byte[].class, codec::serialize);
            conversion.addConverter(byte[].class, Object.class, codec::deserialize);

            return conversion;
        }

        /** 설치된4.1.1 SQL 전체를 실제 quoted schema/column으로 명시한다. */
        @Bean
        SessionRepositoryCustomizer<JdbcIndexedSessionRepository> jdbcSessionPolicy(
                AppProperties properties) {
            return repository -> {
                repository.setDefaultMaxInactiveInterval(
                        properties.auth().preloginSession().timeout());
                repository.setCreateSessionQuery(
                        """
                        INSERT INTO auth_session."SPRING_SESSION" ("PRIMARY_ID", "SESSION_ID", "CREATION_TIME", "LAST_ACCESS_TIME", "MAX_INACTIVE_INTERVAL", "EXPIRY_TIME", "PRINCIPAL_NAME")
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """);
                repository.setCreateSessionAttributeQuery(
                        """
                        INSERT INTO auth_session."SPRING_SESSION_ATTRIBUTES" ("SESSION_PRIMARY_ID", "ATTRIBUTE_NAME", "ATTRIBUTE_BYTES")
                        VALUES (?, ?, ?)
                        """);
                repository.setGetSessionQuery(
                        """
                        SELECT S."PRIMARY_ID", S."SESSION_ID", S."CREATION_TIME", S."LAST_ACCESS_TIME", S."MAX_INACTIVE_INTERVAL", SA."ATTRIBUTE_NAME", SA."ATTRIBUTE_BYTES"
                        FROM auth_session."SPRING_SESSION" S
                        LEFT JOIN auth_session."SPRING_SESSION_ATTRIBUTES" SA ON S."PRIMARY_ID" = SA."SESSION_PRIMARY_ID"
                        WHERE S."SESSION_ID" = ?
                        """);
                repository.setUpdateSessionQuery(
                        """
                        UPDATE auth_session."SPRING_SESSION"
                        SET "SESSION_ID" = ?, "LAST_ACCESS_TIME" = ?, "MAX_INACTIVE_INTERVAL" = ?, "EXPIRY_TIME" = ?, "PRINCIPAL_NAME" = ?
                        WHERE "PRIMARY_ID" = ?
                        """);
                repository.setUpdateSessionAttributeQuery(
                        """
                        UPDATE auth_session."SPRING_SESSION_ATTRIBUTES"
                        SET "ATTRIBUTE_BYTES" = ?
                        WHERE "SESSION_PRIMARY_ID" = ?
                        AND "ATTRIBUTE_NAME" = ?
                        """);
                repository.setDeleteSessionAttributeQuery(
                        """
                        DELETE FROM auth_session."SPRING_SESSION_ATTRIBUTES"
                        WHERE "SESSION_PRIMARY_ID" = ?
                        AND "ATTRIBUTE_NAME" = ?
                        """);
                repository.setDeleteSessionQuery(
                        """
                        DELETE FROM auth_session."SPRING_SESSION"
                        WHERE "SESSION_ID" = ?
                        AND "MAX_INACTIVE_INTERVAL" >= 0
                        """);
                repository.setListSessionsByPrincipalNameQuery(
                        """
                        SELECT S."PRIMARY_ID", S."SESSION_ID", S."CREATION_TIME", S."LAST_ACCESS_TIME", S."MAX_INACTIVE_INTERVAL", SA."ATTRIBUTE_NAME", SA."ATTRIBUTE_BYTES"
                        FROM auth_session."SPRING_SESSION" S
                        LEFT JOIN auth_session."SPRING_SESSION_ATTRIBUTES" SA ON S."PRIMARY_ID" = SA."SESSION_PRIMARY_ID"
                        WHERE S."PRINCIPAL_NAME" = ?
                        """);
                repository.setDeleteSessionsByExpiryTimeQuery(
                        """
                        DELETE FROM auth_session."SPRING_SESSION"
                        WHERE "EXPIRY_TIME" < ?
                        """);
            };
        }
    }
}
