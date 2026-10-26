package com.example.starter.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.Filter;
import jakarta.servlet.RequestDispatcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 실제 DB와 공개 장애 endpoint 없이 HTTP 오류 계약의 회귀를 보호한다. */
class ApiContractTest {
    private static final String MARKER = "SYNTHETIC_ERROR_MARKER";
    private final JsonMapper mapper = JsonMapper.builder().build();
    private MockMvc mvc;
    private ProblemResponses problems;

    /** 격리된 MVC와 실제 공통 처리기를 연결한다. */
    @BeforeEach
    void configureMvc() {
        problems = new ProblemResponses(mapper);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new SyntheticController(),
                        new SafeErrorController(problems))
                .setControllerAdvice(new ApiExceptionHandler(problems),
                        new ProblemDetailBoundary(problems))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper))
                .setValidator(validator).addFilters(new RequestBoundaryFilter(problems)).build();
    }

    /** 원문·중첩 원인·HTML Accept와 노출 유도 query가 공통 500을 바꾸지 않는다. */
    @Test
    void unknownFailureDoesNotExposeDiagnostics() throws Exception {
        MockHttpServletResponse response = mvc.perform(MockMvcRequestBuilders.get("/failure")
                .accept("text/html").queryParam("trace", "true").queryParam("message", "true")
                .queryParam("errors", "true").header("X-Request-ID", MARKER))
                .andReturn().getResponse();
        assertProblem(response, 500, "INTERNAL_ERROR");
        assertNull(org.slf4j.MDC.get("traceId"));
    }

    /** 승인 업무 실패와 MVC 메서드·미디어·파싱 상태를 보존한다. */
    @Test
    void classifiedFailuresPreserveHttpSemantics() throws Exception {
        assertProblem(mvc.perform(MockMvcRequestBuilders.get("/conflict"))
                .andReturn().getResponse(), 409, "CONFLICT");
        MockHttpServletResponse method = mvc.perform(MockMvcRequestBuilders.put("/input"))
                .andReturn().getResponse();
        assertProblem(method, 405, "METHOD_NOT_ALLOWED");
        assertTrue(method.getHeader("Allow").contains("POST"));
        assertProblem(mvc.perform(MockMvcRequestBuilders.post("/input").contentType("text/plain")
                .content(MARKER)).andReturn().getResponse(), 415, "UNSUPPORTED_MEDIA_TYPE");
        assertProblem(mvc.perform(MockMvcRequestBuilders.post("/input")
                .contentType("application/json").content("{\"name\":" + MARKER))
                .andReturn().getResponse(), 400, "MALFORMED_REQUEST");
    }

    /** rejectedValue와 원문 검증 메시지는 반환하지 않고 허용된 field/code만 제공한다. */
    @Test
    void validationExposesOnlyPublicFieldAndCode() throws Exception {
        MockHttpServletResponse response = mvc.perform(MockMvcRequestBuilders.post("/input")
                .contentType("application/json").content("{\"name\":\"\"}"))
                .andReturn().getResponse();
        JsonNode body = assertProblem(response, 400, "VALIDATION_FAILED");
        assertEquals("name", body.path("fieldErrors").get(0).path("field").asString());
        assertEquals("REQUIRED", body.path("fieldErrors").get(0).path("code").asString());
        assertEquals(2, body.path("fieldErrors").get(0).size());
    }

    /** 임의 ProblemDetail 확장과 직접 /error 호출도 동일 허용목록으로 제한한다. */
    @Test
    void problemDetailAndFallbackCannotBypassBoundary() throws Exception {
        assertProblem(mvc.perform(MockMvcRequestBuilders.get("/detail")).andReturn().getResponse(),
                400, "MALFORMED_REQUEST");
        assertProblem(mvc.perform(MockMvcRequestBuilders.get("/error").accept("text/html")
                .requestAttr(RequestDispatcher.ERROR_MESSAGE, MARKER)
                .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new RuntimeException(MARKER))
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404))
                .andReturn().getResponse(), 404, "NOT_FOUND");
        assertProblem(mvc.perform(MockMvcRequestBuilders.get("/error")).andReturn().getResponse(),
                500, "INTERNAL_ERROR");
    }

    /** Session을 포함한 외부 필터 실패도 원문 없이 종료한다. */
    @Test
    void filterFailureIsNormalized() throws Exception {
        Filter failing = (request, response, chain) -> {
            throw new IllegalStateException(MARKER);
        };
        MockMvc filtered = MockMvcBuilders.standaloneSetup(new SyntheticController())
                .addFilters(new RequestBoundaryFilter(problems), failing).build();
        assertProblem(filtered.perform(MockMvcRequestBuilders.get("/ok")).andReturn().getResponse(),
                500, "INTERNAL_ERROR");
    }

    /** 선언 길이와 실제 chunked 바이트 상한, 전송 후 실패의 비추가 출력을 검사한다. */
    @Test
    void oversizedAndCommittedResponsesKeepSafeBoundary() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(new byte[(int) BoundedBodyRequest.MAX_BYTES + 1]);
        BoundedBodyRequest bounded = new BoundedBodyRequest(request);
        assertThrows(ApiException.class, () -> bounded.getInputStream().readAllBytes());
        MockHttpServletResponse oversized = mvc.perform(MockMvcRequestBuilders.post("/input")
                .contentType("application/json").content(new byte[1_048_577]))
                .andReturn().getResponse();
        assertProblem(oversized, 413, "PAYLOAD_TOO_LARGE");
        assertEquals("nosniff", oversized.getHeader("X-Content-Type-Options"));
        assertEquals("default-src 'none'; base-uri 'none'; "
                        + "frame-ancestors 'none'; form-action 'none'",
                oversized.getHeader("Content-Security-Policy"));
        assertEquals("no-store", oversized.getHeader("Cache-Control"));
        MockHttpServletResponse committed = new MockHttpServletResponse();
        committed.getWriter().write("already-sent");
        committed.flushBuffer();
        problems.write(ApiError.INTERNAL_ERROR, request, committed, new RuntimeException(MARKER));
        assertEquals("already-sent", committed.getContentAsString());
        assertFalse(committed.getContentAsString().contains(MARKER));
    }

    /** 성공·생성·204·HEAD와 페이지 경계가 API 계약에 맞는다. */
    @Test
    void successAndPageBoundariesMatchContract() throws Exception {
        MockHttpServletResponse ok = mvc.perform(MockMvcRequestBuilders.get("/ok"))
                .andReturn().getResponse();
        JsonNode body = mapper.readTree(ok.getContentAsByteArray());
        assertEquals(200, ok.getStatus());
        assertEquals(2, body.size());
        assertEquals(0, body.path("data").size());
        assertEquals(ok.getHeader("X-Request-ID"), body.path("traceId").asString());
        MockHttpServletResponse created = mvc.perform(MockMvcRequestBuilders.post("/created"))
                .andReturn().getResponse();
        assertEquals(201, created.getStatus());
        assertEquals("/api/resources/one", created.getHeader("Location"));
        MockHttpServletResponse empty = mvc.perform(MockMvcRequestBuilders.get("/empty"))
                .andReturn().getResponse();
        assertEquals(204, empty.getStatus());
        assertEquals(0, empty.getContentAsByteArray().length);
        assertEquals(0, mvc.perform(MockMvcRequestBuilders.head("/ok")).andReturn()
                .getResponse().getContentAsByteArray().length);
        assertEquals(0, PageData.of(List.of(), 0, 20, 0).totalPages());
        assertEquals(2, PageData.of(List.of(), 0, 20, 21).totalPages());
        assertThrows(ApiException.class, () -> PageData.of(List.of(), -1, 20, 0));
        assertThrows(ApiException.class, () -> PageData.of(List.of(), 0, 101, 0));
    }

    /** 허용 필드·traceId·HTTP 상태와 진단 비공개를 함께 검사한다. */
    private JsonNode assertProblem(MockHttpServletResponse response, int status, String code)
            throws Exception {
        assertEquals(status, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/problem+json"));
        String text = response.getContentAsString();
        assertFalse(text.contains(MARKER));
        assertFalse(text.contains("java.lang"));
        JsonNode body = mapper.readTree(response.getContentAsByteArray());
        assertEquals(status, body.path("status").asInt());
        assertEquals(code, body.path("code").asString());
        assertEquals("about:blank", body.path("type").asString());
        String traceId = body.path("traceId").asString();
        assertEquals(4, UUID.fromString(traceId).version());
        assertEquals(traceId, response.getHeader("X-Request-ID"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        Set<String> allowed = Set.of("type", "title", "status", "code", "traceId", "fieldErrors");

        for (String name : body.propertyNames()) {
            assertTrue(allowed.contains(name), name);
        }

        for (String header : response.getHeaderNames()) {
            assertFalse(response.getHeaders(header).toString().contains(MARKER));
        }

        return body;
    }

    /** 검증 전용 입력이며 공개 앱에는 포함하지 않는다. */
    record SyntheticInput(@NotBlank(message = MARKER) String name) {
    }

    /** 테스트 전용 HTTP 입력으로 공통 처리의 외부 계약을 관측한다. */
    @RestController
    static class SyntheticController {
        /** 빈 목록을 성공 응답으로 반환한다. */
        @GetMapping("/ok")
        ResponseEntity<ApiResponse<List<String>>> ok() {
            return ApiResponses.ok(List.of());
        }

        /** 생성 응답의 Location을 검증한다. */
        @PostMapping("/created")
        ResponseEntity<ApiResponse<String>> created() {
            return ApiResponses.created(URI.create("/api/resources/one"), "one");
        }

        /** 본문 없는 성공을 반환한다. */
        @GetMapping("/empty")
        ResponseEntity<Void> empty() {
            return ApiResponses.noContent();
        }

        /** 진단 정보가 응답으로 전파되지 않는지 검사한다. */
        @GetMapping("/failure")
        void failure() {
            throw new IllegalStateException(MARKER, new IllegalArgumentException(MARKER));
        }

        /** 등록된 업무 실패의 HTTP 상태를 검사한다. */
        @GetMapping("/conflict")
        void conflict() {
            throw new ApiException(ApiError.CONFLICT, new RuntimeException(MARKER));
        }

        /** 승인되지 않은 ProblemDetail 확장을 합성한다. */
        @GetMapping("/detail")
        ProblemDetail detail() {
            ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, MARKER);
            detail.setTitle(MARKER);
            detail.setProperty("exception", MARKER);

            return detail;
        }

        /** 입력 검증만 실행하며 데이터를 저장하지 않는다. */
        @PostMapping("/input")
        void input(@Valid @RequestBody SyntheticInput input) {
        }
    }
}
