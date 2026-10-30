package com.example.starter.common.config;

import com.example.starter.common.web.UtcTimes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/** 업무 JSON 입력의 깊이·중복 키·미등록 필드를 제한한다. */
@Configuration(proxyBeanMethods = false)
public class JsonConfiguration {
    /** 서버가 알지 못하는 JSON 필드를 조용히 무시하지 않는다. */
    @Bean
    JsonMapperBuilderCustomizer strictJsonFields() {
        return builder ->
                builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .addModule(apiValueModule());
    }

    /** 업무 시각·정밀 수량만 문자열로 직렬화하며 프로토콜 정수는 보존한다. */
    public static SimpleModule apiValueModule() {
        SimpleModule module = new SimpleModule("api-values");
        module.addSerializer(
                Instant.class,
                new ValueSerializer<Instant>() {
                    /** UTC 밀리초 문자열을 출력한다. */
                    @Override
                    public void serialize(
                            Instant value, JsonGenerator output, SerializationContext context) {
                        output.writeString(UtcTimes.formatMillis(value));
                    }
                });
        module.addDeserializer(
                Instant.class,
                new ValueDeserializer<Instant>() {
                    /** offset 없는 엄격한 API 시각만 받는다. */
                    @Override
                    public Instant deserialize(JsonParser input, DeserializationContext context) {
                        return UtcTimes.parseMillis(input.getString());
                    }
                });
        module.addSerializer(
                LocalDate.class,
                new ValueSerializer<LocalDate>() {
                    /** 유효한 네 자리 연도 날짜를 출력한다. */
                    @Override
                    public void serialize(
                            LocalDate value, JsonGenerator output, SerializationContext context) {
                        output.writeString(UtcTimes.parseDate(value.toString()).toString());
                    }
                });
        module.addDeserializer(
                LocalDate.class,
                new ValueDeserializer<LocalDate>() {
                    /** 실제 달력에 존재하는 날짜만 받는다. */
                    @Override
                    public LocalDate deserialize(JsonParser input, DeserializationContext context) {
                        return UtcTimes.parseDate(input.getString());
                    }
                });
        module.addSerializer(
                BigDecimal.class,
                new ValueSerializer<BigDecimal>() {
                    /** 지수 표기와 부동소수 변환 없이 정밀 수량을 출력한다. */
                    @Override
                    public void serialize(
                            BigDecimal value, JsonGenerator output, SerializationContext context) {
                        output.writeString(value.toPlainString());
                    }
                });
        module.addDeserializer(
                BigDecimal.class,
                new ValueDeserializer<BigDecimal>() {
                    /** 숫자 JSON과 지수 표현을 허용하지 않는다. */
                    @Override
                    public BigDecimal deserialize(
                            JsonParser input, DeserializationContext context) {
                        if (!input.currentToken().isScalarValue()
                                || !input.currentToken().name().equals("VALUE_STRING")
                                || !input.getString().matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")) {
                            throw new IllegalArgumentException("Decimal string required");
                        }

                        return new BigDecimal(input.getString());
                    }
                });

        return module;
    }

    /** 역직렬화 전에 중복 키와 과도한 중첩을 거부한다. */
    @Bean
    JsonFactoryBuilderCustomizer strictJsonStructure() {
        return builder ->
                builder.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                        .streamReadConstraints(
                                StreamReadConstraints.builder()
                                        .maxNestingDepth(32)
                                        .maxDocumentLength(1_048_576)
                                        .maxStringLength(1_048_576)
                                        .build());
    }
}
