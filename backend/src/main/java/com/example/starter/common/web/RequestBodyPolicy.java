package com.example.starter.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;

/** multipart 여유는 정확한 파일 업로드 요청에만 허용한다. */
public final class RequestBodyPolicy {
    public static final long JSON_BYTES = 1048576;
    public static final long FILE_BYTES = 5242880;
    public static final long MULTIPART_BYTES = FILE_BYTES + 65536;

    /** 정적 HTTP 규칙의 인스턴스 생성을 막는다. */
    private RequestBodyPolicy() {}

    /** 경로/메서드/형식이 모두 일치해야 확대된 container 한도를 사용할 수 있다. */
    public static boolean upload(HttpServletRequest request) {
        return request.getMethod().equals("POST")
                && request.getRequestURI().equals(request.getContextPath() + "/api/files")
                && request.getContentType() != null
                && MediaType.parseMediaType(request.getContentType())
                        .getType()
                        .equalsIgnoreCase("multipart")
                && MediaType.parseMediaType(request.getContentType())
                        .getSubtype()
                        .equalsIgnoreCase("form-data");
    }

    /** 다른 multipart 경로는 parser를 열기 전에 거부한다. */
    public static long limit(HttpServletRequest request) {
        if (request.getContentType() != null) {
            MediaType type;

            try {
                type = MediaType.parseMediaType(request.getContentType());
            } catch (IllegalArgumentException bad) {
                throw new ApiException(ApiError.UNSUPPORTED_MEDIA_TYPE);
            }

            if (type.getType().equalsIgnoreCase("multipart")) {
                if (!upload(request)) {
                    throw new ApiException(ApiError.UNSUPPORTED_MEDIA_TYPE);
                }

                return MULTIPART_BYTES;
            }
        }

        return JSON_BYTES;
    }
}
