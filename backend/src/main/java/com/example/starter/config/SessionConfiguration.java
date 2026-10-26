package com.example.starter.config;

import java.util.HashMap;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.session.web.http.DefaultCookieSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/** Redis 세션의 쿠키·JSON 타입 계약을 모든 인스턴스에서 일치시킨다. */
@Configuration(proxyBeanMethods = false)
public class SessionConfiguration {
    /** HTTP 세션 쿠키와 운영 __Host 속성을 명시한다. */
    @Bean
    DefaultCookieSerializer cookieSerializer(Environment environment) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(environment.getRequiredProperty(
                "server.servlet.session.cookie.name"));
        serializer.setUseSecureCookie(environment.getRequiredProperty(
                "server.servlet.session.cookie.secure", Boolean.class));
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setCookiePath("/");
        serializer.setCookieMaxAge(-1);

        return serializer;
    }

    /** 웹 JSON mapper와 분리한 Security 허용목록 serializer를 Session에 연결한다. */
    @Bean
    RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        JsonMapper mapper = JsonMapper.builder()
                .addModules(SecurityJacksonModules.getModules(getClass().getClassLoader(),
                        BasicPolymorphicTypeValidator.builder().allowIfSubType(Long.class)
                                .allowIfSubType(Integer.class).allowIfSubType(Boolean.class)
                                .allowIfSubType(String.class)))
                .build();

        return new SessionJsonSerializer(mapper);
    }

    /** Session 메타데이터·생성 이벤트·CSRF·SecurityContext의 실제 타입만 허용한다. */
    private static final class SessionJsonSerializer implements RedisSerializer<Object> {
        private static final Set<Class<?>> TYPES = Set.of(String.class, Long.class, Integer.class,
                Boolean.class, HashMap.class, SecurityContextImpl.class, DefaultCsrfToken.class);
        private final JsonMapper mapper;

        /** 공식 Security 모듈이 등록한 제한된 JSON 처리를 재사용한다. */
        private SessionJsonSerializer(JsonMapper mapper) {
            this.mapper = mapper;
        }

        /** 등록되지 않은 세션 객체를 쓰기 전에 거부한다. */
        @Override
        public byte[] serialize(Object value) {
            validate(value);

            try {
                return mapper.writerFor(Object.class).writeValueAsBytes(value);
            } catch (RuntimeException exception) {
                throw new SerializationException("Session JSON encoding failed", exception);
            }
        }

        /** Security의 타입 검증을 거쳐 복원한 뒤 최상위 속성 계약도 확인한다. */
        @Override
        public Object deserialize(byte[] bytes) {
            if (bytes == null || bytes.length == 0) {
                return null;
            }

            Object value;

            try {
                // 저장된 class ID를 별도 Class로 해석하지 않고 항상 타입 검증기를 통과시킨다.
                value = mapper.readValue(bytes, Object.class);
            } catch (RuntimeException exception) {
                throw new SerializationException("Session JSON decoding failed", exception);
            }

            validate(value);

            return value;
        }

        /** 임의 객체나 provider 객체가 묵시적으로 세션 계약을 넓히지 않게 한다. */
        private static void validate(Object value) {
            if (value != null && !TYPES.contains(value.getClass())) {
                throw new SerializationException("Unsupported session attribute type");
            }

            if (value instanceof HashMap<?, ?> map) {
                for (var entry : map.entrySet()) {
                    // 생성 이벤트의 값은 단일 Session 속성이다. 임의 중첩 컨테이너는 허용하지 않는다.
                    if (!(entry.getKey() instanceof String)
                            || entry.getValue() instanceof HashMap) {
                        throw new SerializationException("Unsupported session event structure");
                    }

                    validate(entry.getValue());
                }
            }
        }
    }
}
