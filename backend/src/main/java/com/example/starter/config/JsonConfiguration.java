package com.example.starter.config;

import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;

/** 업무 JSON 입력의 깊이·중복 키·미등록 필드를 제한한다. */
@Configuration(proxyBeanMethods = false)
public class JsonConfiguration {
    /** 서버가 알지 못하는 JSON 필드를 조용히 무시하지 않는다. */
    @Bean
    JsonMapperBuilderCustomizer strictJsonFields() {
        return builder -> builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /** 역직렬화 전에 중복 키와 과도한 중첩을 거부한다. */
    @Bean
    JsonFactoryBuilderCustomizer strictJsonStructure() {
        return builder -> builder.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32)
                        .maxDocumentLength(1_048_576).maxStringLength(1_048_576).build());
    }
}
