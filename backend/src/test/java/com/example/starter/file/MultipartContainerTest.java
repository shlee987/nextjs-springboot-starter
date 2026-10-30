package com.example.starter.file;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.starter.TestPolicies;
import com.example.starter.common.web.RequestBodyPolicy;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 제품 서버·DB·저장 루트 대신 임시 Tomcat과 임시 multipart 디렉터리만 사용한다. */
class MultipartContainerTest {
    @TempDir Path temporary;

    /** 실제 Servlet parser도 chunked 크기·part 수·header 한도를 적용한다. */
    @Test
    void actualContainerEnforcesStreamingAndPartLimits() throws Exception {
        var environment = TestPolicies.environment();
        assertEquals(
                "5MB", environment.getRequiredProperty("spring.servlet.multipart.max-file-size"));
        assertEquals(
                "5308416B",
                environment.getRequiredProperty("spring.servlet.multipart.max-request-size"));
        assertEquals("1", environment.getRequiredProperty("server.tomcat.max-part-count"));
        assertEquals("2KB", environment.getRequiredProperty("server.tomcat.max-part-header-size"));
        var tomcat = new Tomcat();
        tomcat.setBaseDir(temporary.toString());
        tomcat.setHostname("127.0.0.1");
        tomcat.setPort(0);
        var connector = tomcat.getConnector();
        connector.setProperty("address", "127.0.0.1");
        connector.setMaxPartCount(1);
        connector.setMaxPartHeaderSize(2048);
        var context = tomcat.addContext("", temporary.toString());
        var servlet = Tomcat.addServlet(context, "parts", new Parts());
        servlet.setMultipartConfigElement(
                new MultipartConfigElement(
                        temporary.toString(),
                        RequestBodyPolicy.FILE_BYTES,
                        RequestBodyPolicy.MULTIPART_BYTES,
                        0));
        context.addServletMapping("/parts", "parts");

        try {
            tomcat.start();

            try (var client =
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
                var uri = URI.create("http://127.0.0.1:" + connector.getLocalPort() + "/parts");
                assertEquals(200, send(client, uri, multipart(5242880, false, "sample.png")));
                assertEquals(413, send(client, uri, multipart(5242881, false, "sample.png")));
                assertEquals(413, send(client, uri, multipart(10, true, "sample.png")));
                assertEquals(
                        413, send(client, uri, multipart(10, false, "x".repeat(2100) + ".png")));
            }
        } finally {
            tomcat.stop();
            tomcat.destroy();
        }
    }

    /** 길이를 알리지 않는 body publisher로 Content-Length와 독립적인 parser 제한을 확인한다. */
    private int send(HttpClient client, URI uri, byte[] bytes) throws Exception {
        var request =
                HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "multipart/form-data; boundary=synthetic")
                        .POST(
                                HttpRequest.BodyPublishers.ofInputStream(
                                        () -> new ByteArrayInputStream(bytes)))
                        .build();

        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /** 실제 multipart framing이며 파일 바이트는 이미지 검사 없이 parser에서만 소비한다. */
    private byte[] multipart(int size, boolean extra, String filename) throws IOException {
        var bytes = new ByteArrayOutputStream();
        bytes.write(
                ("--synthetic\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                                + filename
                                + "\"\r\nContent-Type: image/png\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
        bytes.write(new byte[size]);

        if (extra) {
            bytes.write(
                    "\r\n--synthetic\r\nContent-Disposition: form-data; name=\"owner\"\r\n\r\n2"
                            .getBytes(StandardCharsets.US_ASCII));
        }

        bytes.write("\r\n--synthetic--\r\n".getBytes(StandardCharsets.US_ASCII));
        return bytes.toByteArray();
    }

    /** 검증 대상은 컨테이너 multipart 처리이며 제품 권한 처리는 별도 chain 시험이 담당한다. */
    private static final class Parts extends HttpServlet {
        /** 정상 part만200으로 표시하고 parser 제한 초과는413으로 구분한다. */
        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws IOException {
            try {
                for (var part : request.getParts()) {
                    part.delete();
                }
                response.setStatus(200);
            } catch (IllegalStateException | ServletException failure) {
                response.setStatus(413);
            }
        }
    }
}
