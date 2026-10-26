package com.example.starter.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Content-Length가 없는 스트림도 parser 진입 전 최대 1 MiB만 읽는다. */
final class BoundedBodyRequest extends HttpServletRequestWrapper {
    static final long MAX_BYTES = 1_048_576;
    private ServletInputStream bounded;

    /** 원본 요청의 인증·헤더·attribute 계약을 유지한다. */
    BoundedBodyRequest(HttpServletRequest request) {
        super(request);
    }

    /** 같은 스트림에서 누적 바이트를 제한하여 chunked 입력의 상한 우회를 막는다. */
    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (bounded == null) {
            ServletInputStream source = super.getInputStream();
            bounded = new ServletInputStream() {
                private long count;

                /** 입력 바이트를 읽은 뒤 누적 상한 초과를 거부한다. */
                @Override
                public int read() throws IOException {
                    int value = source.read();

                    if (value != -1 && ++count > MAX_BYTES) {
                        throw new ApiException(ApiError.PAYLOAD_TOO_LARGE);
                    }

                    return value;
                }

                /** 벌크 읽기도 상한 다음 한 바이트까지만 소비한다. */
                @Override
                public int read(byte[] bytes, int offset, int length) throws IOException {
                    int amount = source.read(bytes, offset,
                            (int) Math.min(length, MAX_BYTES - count + 1));

                    if (amount > 0) {
                        count += amount;
                    }

                    if (count > MAX_BYTES) {
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

    /** Reader 경로도 같은 바이트 상한을 통과하게 한다. JSON 기본 인코딩은 UTF-8이다. */
    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}
