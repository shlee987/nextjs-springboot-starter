package com.example.starter.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** multipart 예외가 JSON 한도나 중복 query 정책을 완화하지 않는지 검사한다. */
class UploadBodyPolicyTest {
    /** 확대 한도는 정확한 메서드·경로·형식 조합에만 적용한다. */
    @Test
    void multipartAllowanceIsSpecificAndUnknownLengthsAreBounded() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/files");
        request.setContentType("multipart/form-data; boundary=x");
        assertEquals(5308416, RequestBodyPolicy.limit(request));
        request.setContent(new byte[5308417]);
        var bounded = new BoundedBodyRequest(request);
        assertThrows(ApiException.class, () -> bounded.getInputStream().readAllBytes());
        assertThrows(ApiException.class, () -> bounded.getInputStream().read());
        assertTrue(bounded.getParameterMap().isEmpty());

        for (String path : new String[] {"/api/files/", "/api/files/x", "/api/other"}) {
            request.setRequestURI(path);
            assertEquals(
                    ApiError.UNSUPPORTED_MEDIA_TYPE,
                    assertThrows(ApiException.class, () -> RequestBodyPolicy.limit(request))
                            .error());
        }

        request.setRequestURI("/api/files");
        request.setMethod("PUT");
        assertThrows(ApiException.class, () -> RequestBodyPolicy.limit(request));
        request.setContentType("application/json");
        assertEquals(1048576, RequestBodyPolicy.limit(request));
        request.setContent(new byte[1048577]);
        assertThrows(
                ApiException.class,
                () -> new BoundedBodyRequest(request).getInputStream().readAllBytes());
    }

    /** 같은 이름의 인코딩 변형도 거부하고 정상 query 값은 그대로 둔다. */
    @Test
    void scalarDuplicatesCannotSelectAnArbitraryValue() throws Exception {
        for (String query :
                new String[] {"page=0&page=1", "page=0&%70age=1", "q=a&q=b", "%=bad", "q=x&"}) {
            var request = new MockHttpServletRequest("GET", "/api/users");
            request.setQueryString(query);
            assertEquals(
                    ApiError.MALFORMED_REQUEST,
                    assertThrows(
                                    ApiException.class,
                                    () ->
                                            new ScalarQueryFilter()
                                                    .doFilter(
                                                            request,
                                                            new MockHttpServletResponse(),
                                                            (a, b) -> fail("Duplicate accepted")))
                            .error());
        }

        var request = new MockHttpServletRequest("GET", "/api/users");
        String query = "q=O%27Reilly%20OR%201%3D1%20%3Cscript%3E&page=0&size=20";
        request.setQueryString(query);
        var calls = new AtomicInteger();
        new ScalarQueryFilter()
                .doFilter(
                        request,
                        new MockHttpServletResponse(),
                        (a, b) -> {
                            assertEquals(query, ((HttpServletRequest) a).getQueryString());
                            calls.incrementAndGet();
                        });
        assertEquals(1, calls.get());
    }
}
