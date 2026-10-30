package com.example.starter.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 비밀번호 DTO는 기본 record toString으로 원문을 노출하지 않는다. */
public final class AuthRequests {
    /** 요청 DTO namespace의 인스턴스 생성을 막는다. */
    private AuthRequests() {}

    /** 기존 비밀번호 로그인에는 신규 비밀번호 최소 길이를 재강제하지 않는다. */
    public record Login(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9._-]{2,49}") String loginId,
            @NotBlank
                    @CodePointLength
                    @Schema(minLength = 1, maxLength = 128, accessMode = AccessMode.WRITE_ONLY)
                    String password) {
        /** 원문 credential 진단 출력을 차단한다. */
        @Override
        public String toString() {
            return "Login[redacted]";
        }
    }

    /** 비밀번호 변경의 새 값에만 생성 정책을 적용한다. */
    public record Password(
            @NotBlank
                    @CodePointLength
                    @Schema(minLength = 1, maxLength = 128, accessMode = AccessMode.WRITE_ONLY)
                    String currentPassword,
            @NotBlank
                    @CodePointLength(min = 15)
                    @Schema(minLength = 15, maxLength = 128, accessMode = AccessMode.WRITE_ONLY)
                    String newPassword) {
        /** 변경 전후 비밀번호 모두 로그에 남기지 않는다. */
        @Override
        public String toString() {
            return "Password[redacted]";
        }
    }
}
