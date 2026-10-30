package com.example.starter.common.config;

import com.example.starter.auth.web.AuthCookies;
import java.util.HashMap;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.session.web.http.DefaultCookieSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/** 선택 세션의 쿠키·JSON 타입 계약을 모든 인스턴스에서 일치시킨다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.auth.prelogin-session.enabled", havingValue = "true")
public class SessionConfiguration {
    /** 임시 외부 로그인 세션만 별도 host-only 쿠키를 사용한다. */
    @Bean
    DefaultCookieSerializer cookieSerializer(AuthCookies cookies, AppProperties properties) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(cookies.name("prelogin"));
        serializer.setUseSecureCookie(cookies.secure());
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setCookiePath("/");
        serializer.setCookieMaxAge(
                Math.toIntExact(properties.auth().preloginSession().timeout().toSeconds()));

        return serializer;
    }

    /** 웹 JSON mapper와 분리한 Security 허용목록 serializer를 Session에 연결한다. */
    @Bean
    SessionJsonCodec preloginJsonCodec() {
        JsonMapper mapper =
                JsonMapper.builder()
                        .addModules(
                                SecurityJacksonModules.getModules(
                                        getClass().getClassLoader(),
                                        BasicPolymorphicTypeValidator.builder()
                                                .allowIfSubType(Long.class)
                                                .allowIfSubType(Integer.class)
                                                .allowIfSubType(Boolean.class)
                                                .allowIfSubType(String.class)))
                        .build();

        return new SessionJsonCodec(mapper);
    }

    /** Session 메타데이터·생성 이벤트·CSRF·SecurityContext의 실제 타입만 허용한다. */
    public static final class SessionJsonCodec {
        private static final Set<Class<?>> TYPES =
                Set.of(
                        String.class,
                        Long.class,
                        Integer.class,
                        Boolean.class,
                        HashMap.class,
                        SecurityContextImpl.class,
                        DefaultCsrfToken.class);
        private final JsonMapper mapper;

        /** 공식 Security 모듈이 등록한 제한된 JSON 처리를 재사용한다. */
        private SessionJsonCodec(JsonMapper mapper) {
            this.mapper = mapper;
        }

        /** 등록되지 않은 세션 객체를 쓰기 전에 거부한다. */
        public byte[] serialize(Object value) {
            validate(value);

            try {
                return mapper.writerFor(Object.class).writeValueAsBytes(value);
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Session JSON encoding failed", exception);
            }
        }

        /** Security의 타입 검증을 거쳐 복원한 뒤 최상위 속성 계약도 확인한다. */
        public Object deserialize(byte[] bytes) {
            if (bytes == null || bytes.length == 0) {
                return null;
            }

            Object value;

            try {
                // 저장된 class ID를 별도 Class로 해석하지 않고 항상 타입 검증기를 통과시킨다.
                value = mapper.readValue(bytes, Object.class);
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Session JSON decoding failed", exception);
            }

            validate(value);

            return value;
        }

        /** 임의 객체나 provider 객체가 묵시적으로 세션 계약을 넓히지 않게 한다. */
        private static void validate(Object value) {
            if (value != null && !TYPES.contains(value.getClass())) {
                throw new IllegalArgumentException("Unsupported session attribute type");
            }

            if (value instanceof HashMap<?, ?> map) {
                for (var entry : map.entrySet()) {
                    // 생성 이벤트의 값은 단일 Session 속성이다. 임의 중첩 컨테이너는 허용하지 않는다.
                    if (!(entry.getKey() instanceof String)
                            || entry.getValue() instanceof HashMap) {
                        throw new IllegalArgumentException("Unsupported session event structure");
                    }

                    validate(entry.getValue());
                }
            }
        }
    }
}
