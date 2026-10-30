package com.example.starter.common.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

/** 일반 요청은1MiB, 정확한 파일 업로드는 제한된 multipart 크기로 스트림을 제한한다. */
final class BoundedBodyRequest extends HttpServletRequestWrapper {
    static final long MAX_BYTES = 1_048_576;
    private ServletInputStream bounded;
    private final long maximum;
    private final boolean upload;

    /** 원본 요청의 인증·헤더·attribute 계약을 유지한다. */
    BoundedBodyRequest(HttpServletRequest request) {
        super(request);
        maximum = RequestBodyPolicy.limit(request);
        upload = maximum == RequestBodyPolicy.MULTIPART_BYTES;
    }

    /** 같은 스트림에서 누적 바이트를 제한하여 chunked 입력의 상한 우회를 막는다. */
    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (bounded == null) {
            ServletInputStream source = super.getInputStream();
            bounded =
                    new ServletInputStream() {
                        private long count;

                        /** 입력 바이트를 읽은 뒤 누적 상한 초과를 거부한다. */
                        @Override
                        public int read() throws IOException {
                            if (count > maximum) {
                                throw new ApiException(ApiError.PAYLOAD_TOO_LARGE);
                            }
                            int value = source.read();

                            if (value != -1 && ++count > maximum) {
                                throw new ApiException(ApiError.PAYLOAD_TOO_LARGE);
                            }

                            return value;
                        }

                        /** 벌크 읽기도 상한 다음 한 바이트까지만 소비한다. */
                        @Override
                        public int read(byte[] bytes, int offset, int length) throws IOException {
                            if (length == 0) {
                                return 0;
                            }
                            if (count > maximum) {
                                throw new ApiException(ApiError.PAYLOAD_TOO_LARGE);
                            }
                            int amount =
                                    source.read(
                                            bytes,
                                            offset,
                                            (int) Math.min(length, maximum - count + 1));

                            if (amount > 0) {
                                count += amount;
                            }

                            if (count > maximum) {
                                throw new ApiException(ApiError.PAYLOAD_TOO_LARGE);
                            }

                            return amount;
                        }

                        /** 원본 Servlet의 읽기 완료 상태를 유지한다. */
                        @Override
                        public boolean isFinished() {
                            return source.isFinished();
                        }

                        /** 비동기 읽기 가능 상태를 원본에 위임한다. */
                        @Override
                        public boolean isReady() {
                            return source.isReady();
                        }

                        /** 비동기 listener를 원본 스트림에 등록한다. */
                        @Override
                        public void setReadListener(ReadListener listener) {
                            source.setReadListener(listener);
                        }
                    };
        }

        return bounded;
    }

    /** 업로드 CSRF 누락 시 form fallback이 인증 전에 getParts를 열지 않게 한다. */
    @Override
    public String getParameter(String name) {
        return upload ? null : super.getParameter(name);
    }

    /** 업로드는 file part 하나만 허용하므로 form/query 자동 바인딩을 사용하지 않는다. */
    @Override
    public Map<String, String[]> getParameterMap() {
        return upload ? Map.of() : super.getParameterMap();
    }

    /** form fallback의 다른 경로도 원본 multipart parser에 접근하지 않는다. */
    @Override
    public String[] getParameterValues(String name) {
        return upload ? null : super.getParameterValues(name);
    }

    /** 선언한 multipart 외 form 속성은 없다. */
    @Override
    public Enumeration<String> getParameterNames() {
        return upload ? Collections.emptyEnumeration() : super.getParameterNames();
    }

    /** Reader 경로도 같은 바이트 상한을 통과하게 한다. JSON 기본 인코딩은 UTF-8이다. */
    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}
