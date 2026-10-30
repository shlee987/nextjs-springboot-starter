package com.example.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.audit.web.AuditController;
import com.example.starter.auth.config.SigningKeys;
import com.example.starter.auth.web.AuthController;
import com.example.starter.auth.web.AuthCookies;
import com.example.starter.common.config.JsonConfiguration;
import com.example.starter.common.config.OpenApiConfiguration;
import com.example.starter.department.web.DepartmentController;
import com.example.starter.file.web.FileController;
import com.example.starter.user.web.UserController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

/** 실제 Controller/springdoc만 읽는 문서 추출 컨텍스트다. 제품 앱과 외부 저장소는 시작하지 않는다. */
@SpringBootTest(
        classes = OpenApiExportTest.SpecificationContext.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "spring.flyway.enabled=false",
            "spring.main.banner-mode=off",
            "spring.profiles.active=local"
        })
@ActiveProfiles("local")
class OpenApiExportTest {
    /** 실제 springdoc HTTP 응답을 검증한 뒤 backend 빌드 산출물에 저장한다. */
    @Test
    void exportsActualControllerSchemas(WebApplicationContext context) throws Exception {
        assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
        assertTrue(context.getBeansOfType(RedisConnectionFactory.class).isEmpty());
        assertTrue(context.getBeansOfType(SigningKeys.class).isEmpty());
        var response =
                MockMvcBuilders.webAppContextSetup(context)
                        .build()
                        .perform(MockMvcRequestBuilders.get("/v3/api-docs"))
                        .andReturn()
                        .getResponse();
        assertEquals(200, response.getStatus());
        JsonMapper json = JsonMapper.builder().build();
        var spec = json.readTree(response.getContentAsString());
        assertEquals(16, spec.get("paths").size());
        assertTrue(spec.get("components").get("schemas").size() > 15);
        assertEquals(
                "getCurrentUser", spec.at("/paths/~1api~1auth~1me/get/operationId").asString());
        assertTrue(spec.at("/paths/~1api~1auth~1logout/post/responses/204").isObject());
        assertFalse(
                spec.at(
                                "/paths/~1api~1auth~1login/post/responses/200/content/application~1json/schema")
                        .isMissingNode());
        assertTrue(
                spec.at("/components/schemas/Profile/properties/email/type")
                        .toString()
                        .contains("null"));
        assertTrue(spec.at("/components/schemas/Profile/required").toString().contains("userId"));
        assertFalse(spec.at("/components/securitySchemes/bearerAuth").isObject());

        assertTrue(spec.at("/paths/~1api~1files/post/requestBody/required").asBoolean());
        assertEquals(
                "binary",
                spec.at(
                                "/paths/~1api~1files/post/requestBody/content/multipart~1form-data/schema/properties/file/format")
                        .asString());
        assertTrue(
                spec.at("/paths/~1api~1files/post/responses/201/content/application~1json/schema")
                        .isObject());
        assertEquals(
                "binary",
                spec.at(
                                "/paths/~1api~1files~1{fileId}~1content/get/responses/200/content/image~1png/schema/format")
                        .asString());
        assertTrue(
                spec.at(
                                "/paths/~1api~1files~1{fileId}~1content/get/responses/200/headers/Content-Disposition")
                        .isObject());
        assertTrue(spec.at("/paths/~1api~1files~1{fileId}/delete/responses/204").isObject());

        for (var reference : spec.findValues("$ref")) {
            String pointer = reference.asString();
            assertTrue(pointer.startsWith("#/components/schemas/"));
            assertTrue(spec.at(pointer.substring(1)).isObject(), pointer);
        }
        Path output = Path.of("build", "openapi", "openapi.json");
        Files.createDirectories(output.getParent());
        Files.writeString(
                output, json.writerWithDefaultPrettyPrinter().writeValueAsString(spec) + "\n");
    }

    /** 외부 자동 설정과 component scan 없이 문서 작성에 필요한 bean만 등록한다. */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(
            exclude = {
                DataSourceAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class
            })
    @Import({OpenApiConfiguration.class, JsonConfiguration.class})
    static class SpecificationContext {
        /** metadata 조회에서 서비스를 실행할 수 없도록 Controller만 생성한다. */
        @Bean
        AuthController auth() {
            return new AuthController(null, null, null, null, null);
        }

        /** 실제 Controller 메서드/DTO를 읽으며 업무 메서드는 호출하지 않는다. */
        @Bean
        UserController users() {
            return new UserController(null);
        }

        /** 파일 API도 실제 Controller/DTO에서 읽고 파일/DB를 실행하지 않는다. */
        @Bean
        FileController files() {
            return new FileController(null);
        }

        /** 부서 계약도 실제 Controller 타입에서 생성한다. */
        @Bean
        DepartmentController departments() {
            return new DepartmentController(null);
        }

        /** 감사 조회 allowlist DTO를 실제 메서드에서 가져온다. */
        @Bean
        AuditController audits() {
            return new AuditController(null);
        }

        /** 문서에는 local의 실제 쿠키명을 사용한다. */
        @Bean
        AuthCookies cookies(Environment environment) {
            return new AuthCookies(environment, Clock.systemUTC());
        }
    }
}
