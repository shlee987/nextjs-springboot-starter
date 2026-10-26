package com.example.starter.web;

import java.net.URI;
import org.springframework.http.ResponseEntity;

/** Controller의 명시적 성공 반환에서 상태와 요청 식별자를 통일한다. */
public final class ApiResponses {
    /** 정적 반환 도구의 인스턴스 생성을 막는다. */
    private ApiResponses() {
    }

    /** 업무 DTO를 200 JSON envelope로 반환한다. */
    public static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok().header("X-Request-ID", RequestTrace.current())
                .body(new ApiResponse<>(data, RequestTrace.current()));
    }

    /** 서버가 구성한 자원 URI와 업무 DTO를 201로 반환한다. */
    public static <T> ResponseEntity<ApiResponse<T>> created(URI location, T data) {
        return ResponseEntity.created(location).header("X-Request-ID", RequestTrace.current())
                .body(new ApiResponse<>(data, RequestTrace.current()));
    }

    /** 본문 없이 204와 요청 식별자를 반환한다. */
    public static ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().header("X-Request-ID", RequestTrace.current()).build();
    }
}
