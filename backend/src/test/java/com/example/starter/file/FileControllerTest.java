package com.example.starter.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.common.web.ApiException;
import com.example.starter.common.web.ApiExceptionHandler;
import com.example.starter.common.web.ProblemDetailBoundary;
import com.example.starter.common.web.ProblemResponses;
import com.example.starter.file.service.FileService;
import com.example.starter.file.web.FileController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockPart;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import tools.jackson.databind.json.JsonMapper;

/** 실제 컨트롤러 응답과 MVC 오류 경계이며 Servlet multipart 파서 시험과 구분한다. */
class FileControllerTest {
    /** 다음 시험에 인증 주체가 남지 않게 한다. */
    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** 다운로드 헤더·바이트·스트림 닫힘과 삭제204를 확인한다. */
    @Test
    void downloadIsAttachmentAndDeleteHasNoBody() throws Exception {
        var fixture = new FileServiceTest.Fixture();
        FileServiceTest.actor(1);
        var file = fixture.service.upload(fixture.input());
        var mvc = mvc(fixture);
        var response =
                mvc.perform(MockMvcRequestBuilders.get("/api/files/" + file.fileId() + "/content"))
                        .andReturn()
                        .getResponse();
        assertEquals(200, response.getStatus());
        assertEquals("image/png", response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").startsWith("attachment;"));
        assertTrue(response.getHeader("Content-Disposition").contains("filename*=UTF-8''"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("none", response.getHeader("Accept-Ranges"));
        assertEquals(3, response.getContentLength());
        assertArrayEquals(new byte[] {1, 2, 3}, response.getContentAsByteArray());
        assertTrue(fixture.store.closed);
        var deleted =
                mvc.perform(MockMvcRequestBuilders.delete("/api/files/" + file.fileId()))
                        .andReturn()
                        .getResponse();
        assertEquals(204, deleted.getStatus());
        assertEquals(0, deleted.getContentAsByteArray().length);
    }

    /** 실제 HEAD 본문 advice와 함께 반복 호출해 열린 파일 핸들이 남지 않는지 검사한다. */
    @Test
    void headClosesResourceAndPreservesOwnershipAuditAndHeaders() throws Exception {
        var fixture = new FileServiceTest.Fixture();
        FileServiceTest.actor(1);
        var file = fixture.service.upload(fixture.input());
        var mvc = mvc(fixture);
        String path = "/api/files/" + file.fileId() + "/content";

        for (int index = 1; index <= 3; index++) {
            var head = mvc.perform(MockMvcRequestBuilders.head(path)).andReturn().getResponse();
            assertEquals(200, head.getStatus());
            assertEquals(0, head.getContentAsByteArray().length);
            assertEquals("image/png", head.getContentType());
            assertEquals(3, head.getContentLength());
            assertTrue(head.getHeader("Content-Disposition").startsWith("attachment;"));
            assertTrue(head.getHeader("Content-Disposition").contains("filename*=UTF-8''"));
            assertEquals("no-store", head.getHeader("Cache-Control"));
            assertEquals("nosniff", head.getHeader("X-Content-Type-Options"));
            assertEquals(index, fixture.store.opened);
            assertEquals(index, fixture.store.closeCount);
        }

        var get = mvc.perform(MockMvcRequestBuilders.get(path)).andReturn().getResponse();
        assertEquals(200, get.getStatus());
        assertArrayEquals(new byte[] {1, 2, 3}, get.getContentAsByteArray());
        assertEquals(4, fixture.store.opened);
        assertEquals(4, fixture.store.closeCount);
        assertEquals(
                4,
                fixture.events.stream()
                        .filter(
                                event ->
                                        event.actionCode().equals("READ")
                                                && event.outcome().equals("STARTED"))
                        .count());
        assertEquals(
                4,
                fixture.events.stream()
                        .filter(
                                event ->
                                        event.actionCode().equals("READ")
                                                && event.outcome().equals("SUCCESS"))
                        .count());

        FileServiceTest.actor(2);
        var denied = mvc.perform(MockMvcRequestBuilders.head(path)).andReturn().getResponse();
        assertEquals(404, denied.getStatus());
        assertEquals(0, denied.getContentAsByteArray().length);
        assertEquals("no-store", denied.getHeader("Cache-Control"));
        assertTrue(denied.getHeader("X-Request-ID").matches("[0-9a-f-]{36}"));
        var error = mvc.perform(MockMvcRequestBuilders.get(path)).andReturn().getResponse();
        assertEquals(404, error.getStatus());
        assertTrue(error.getContentAsString().contains("NOT_FOUND"));
        assertFalse(error.getContentAsString().contains(file.originalName()));
        assertEquals(4, fixture.store.opened);
    }

    /** 필수 감사가 실패하는 HEAD도 오류 본문과 열린 핸들을 남기지 않는다. */
    @Test
    void headAuditFailureStillClosesHandleAndReturnsSafeError() throws Exception {
        var fixture = new FileServiceTest.Fixture();
        FileServiceTest.actor(1);
        var file = fixture.service.upload(fixture.input());
        fixture.failAudit = true;
        var response =
                mvc(fixture)
                        .perform(
                                MockMvcRequestBuilders.head(
                                        "/api/files/" + file.fileId() + "/content"))
                        .andReturn()
                        .getResponse();
        assertEquals(503, response.getStatus());
        assertEquals(0, response.getContentAsByteArray().length);
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals(1, fixture.store.opened);
        assertEquals(1, fixture.store.closeCount);
    }

    /** 제품의 본문 advice와 오류 advice를 컨트롤러에 함께 연결한다. */
    private MockMvc mvc(FileServiceTest.Fixture fixture) {
        var problems = new ProblemResponses(JsonMapper.builder().build());

        return MockMvcBuilders.standaloneSetup(new FileController(fixture.service))
                .setControllerAdvice(
                        new ProblemDetailBoundary(problems),
                        new ApiExceptionHandler(problems, null))
                .build();
    }

    /** 허용하지 않은 owner part는 메타데이터를 만들기 전에 거부한다. */
    @Test
    void multipartOwnerInjectionIsRejected() throws Exception {
        var fixture = new FileServiceTest.Fixture();
        FileServiceTest.actor(1);
        var mvc =
                MockMvcBuilders.standaloneSetup(new FileController(fixture.service))
                        .setControllerAdvice(
                                new ApiExceptionHandler(
                                        new ProblemResponses(JsonMapper.builder().build()), null))
                        .build();
        var request =
                MockMvcRequestBuilders.multipart("/api/files")
                        .file(fixture.input())
                        .part(new MockPart("file", "sample.png", new byte[] {1}))
                        .part(new MockPart("ownerUserId", "2".getBytes()));
        assertEquals(400, mvc.perform(request).andReturn().getResponse().getStatus());
        assertEquals(0, fixture.db.calls);

        for (String invalid : new String[] {"1-1-1-1-1", "../secret", "NOT-A-UUID"}) {
            assertThrows(ApiException.class, () -> FileService.id(invalid));
        }
    }

    /** Spring의 크기 초과413은 일반 multipart400 처리보다 우선한다. */
    @Test
    void multipartErrorsPreserveSizeStatusAndHideParserDetails() throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(new MultipartFailure())
                        .setControllerAdvice(
                                new ApiExceptionHandler(
                                        new ProblemResponses(JsonMapper.builder().build()), null))
                        .build();
        var large = mvc.perform(MockMvcRequestBuilders.get("/large")).andReturn().getResponse();
        assertEquals(413, large.getStatus());
        var malformed =
                mvc.perform(MockMvcRequestBuilders.get("/malformed")).andReturn().getResponse();
        assertEquals(400, malformed.getStatus());
        assertFalse(malformed.getContentAsString().contains("SYNTHETIC_SECRET"));
    }

    /** 시험 중에만 Servlet 파서 실패를 MVC 경계에 전달한다. */
    @RestController
    static class MultipartFailure {
        /** 크기 예외의 구체적인 framework handler를 실행한다. */
        @GetMapping("/large")
        public void large() {
            throw new MaxUploadSizeExceededException(5242880);
        }

        /** 잘못된 framing 원문을 응답에서 숨긴다. */
        @GetMapping("/malformed")
        public void malformed() {
            throw new MultipartException("SYNTHETIC_SECRET parser detail");
        }
    }
}
