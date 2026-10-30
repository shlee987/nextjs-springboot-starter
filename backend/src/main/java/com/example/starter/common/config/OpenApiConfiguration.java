package com.example.starter.common.config;

import com.example.starter.auth.web.AuthCookies;
import com.example.starter.common.web.ApiProblem;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 실제 typed Controller에서 생성할 명세에 공통 UTC·쿠키·오류 계약을 적용한다. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {
    /** access cookie와 CSRF header만 정의하며 별도 Bearer 입력은 제공하지 않는다. */
    @Bean
    OpenAPI starterOpenApi(AuthCookies cookies) {
        SpringDocUtils.getConfig()
                .replaceWithSchema(
                        Instant.class,
                        new StringSchema()
                                .pattern(
                                        "^[0-9]{4}-[0-9]{2}-[0-9]{2}"
                                                + " [0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}$")
                                .description("UTC 시각, uuuu-MM-dd HH:mm:ss.SSS. offset/Z 없음"));
        SpringDocUtils.getConfig()
                .replaceWithSchema(
                        BigDecimal.class,
                        new StringSchema()
                                .pattern("^-?(0|[1-9][0-9]*)(\\.[0-9]+)?$")
                                .description("정밀 decimal 문자열, 지수 표기 금지"));
        return new OpenAPI()
                .info(new Info().title("Starter API").version("1.0"))
                .servers(List.of(new Server().url("/")))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "cookieAuth",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.APIKEY)
                                                .in(SecurityScheme.In.COOKIE)
                                                .name(cookies.name("access")))
                                .addSecuritySchemes(
                                        "csrfHeader",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.APIKEY)
                                                .in(SecurityScheme.In.HEADER)
                                                .name("X-CSRF-TOKEN")));
    }

    /** 성공 응답은 메서드 타입에서 생성하고 공통 오류는 실제 ApiProblem 모델로 추가한다. */
    @Bean
    OpenApiCustomizer contractCustomizer() {
        return api -> {
            ModelConverters.getInstance()
                    .readAll(ApiProblem.class)
                    .forEach(api.getComponents()::addSchemas);

            for (var path : api.getPaths().values()) {
                for (var entry : path.readOperationsMap().entrySet()) {
                    configureOperation(entry.getKey(), entry.getValue());
                }
            }

            for (Schema<?> schema : api.getComponents().getSchemas().values()) {
                configureSchema(schema);
            }
        };
    }

    /** 공개 인증과 보호 API의 쿠키·CSRF·HTTP 상태 명세를 구분한다. */
    private void configureOperation(HttpMethod method, Operation operation) {
        String id = operation.getOperationId();
        boolean publicAuth = List.of("getCsrf", "login", "refresh", "logout").contains(id);
        boolean unsafe = List.of("POST", "PUT", "PATCH", "DELETE").contains(method.name());
        SecurityRequirement requirement = new SecurityRequirement();

        if (!publicAuth) {
            requirement.addList("cookieAuth");
        }

        if (unsafe) {
            requirement.addList("csrfHeader");
        }

        operation.setSecurity(requirement.isEmpty() ? List.of() : List.of(requirement));
        Schema<?> problem = new Schema<>().$ref("#/components/schemas/ApiProblem");
        Content content =
                new Content()
                        .addMediaType("application/problem+json", new MediaType().schema(problem));

        for (String code :
                List.of("400", "401", "403", "404", "409", "413", "415", "429", "500", "503")) {
            operation
                    .getResponses()
                    .addApiResponse(
                            code,
                            new ApiResponse()
                                    .description("공통 오류 code·traceId; 원문 예외/개인정보 미포함")
                                    .content(content));
        }

        if (List.of("logout", "logoutAll", "changePassword", "deleteFile").contains(id)) {
            operation.getResponses().remove("200");
            operation.getResponses().addApiResponse("204", new ApiResponse().description("본문 없음"));
        }
    }

    /** 항상 출력되는 record 필드와 명시 null·ID 문자열을 실제 JSON 계약에 맞춘다. */
    private void configureSchema(Schema<?> schema) {
        if (schema.getProperties() == null) {
            return;
        }

        schema.setRequired(
                new ArrayList<>(
                        schema.getProperties().keySet().stream()
                                .filter(name -> !name.equals("fieldErrors"))
                                .toList()));

        for (var entry : schema.getProperties().entrySet()) {
            String name = entry.getKey();
            Schema<?> property = entry.getValue();

            if (List.of("userId", "actorUserId", "targetUserId", "departmentId", "parentId")
                    .contains(name)) {
                property.setPattern("^[1-9][0-9]{0,18}$");
            }

            if (List.of(
                            "email",
                            "parentId",
                            "actorUserId",
                            "targetUserId",
                            "targetType",
                            "targetId",
                            "reasonCode")
                    .contains(name)) {
                property.setType(null);
                property.setTypes(new LinkedHashSet<>(List.of("string", "null")));
            }
        }
    }
}
