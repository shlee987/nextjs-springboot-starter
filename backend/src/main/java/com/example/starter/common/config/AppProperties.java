package com.example.starter.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 인증·감사·로그 정책을 타입으로 묶고 시작 시 필수값을 검증한다. */
@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @NotBlank String publicOrigin,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,99}") String serverId,
        @Valid @NotNull Auth auth,
        @Valid @NotNull Logging logging,
        @Valid @NotNull Audit audit,
        @Valid @NotNull Bootstrap bootstrap) {
    /** 인증 상태와 선택 외부 로그인 세션의 정책이다. */
    public record Auth(
            @NotNull StateStore stateStore,
            @NotBlank String namespace,
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,31}") String namespaceGeneration,
            @NotNull ConcurrentLogin concurrentLogin,
            @NotNull Duration accessTtl,
            @NotNull Duration idleTimeout,
            @NotNull Duration absoluteTimeout,
            @Valid @NotNull Jwt jwt,
            @Valid @NotNull FailedLogin failedLogin,
            @Valid @NotNull PreloginSession preloginSession,
            @Valid @NotNull Csrf csrf) {
        /** 상태 수명이 양수이고 access/idle이 절대 만료를 넘지 않는지 확인한다. */
        @AssertTrue
        public boolean isLifetimeValid() {
            return positive(accessTtl)
                    && positive(idleTimeout)
                    && positive(absoluteTimeout)
                    && absoluteTimeout.compareTo(idleTimeout) >= 0
                    && absoluteTimeout.compareTo(accessTtl) >= 0;
        }
    }

    /** 서명 키와 검증 대상의 설정이다. local 개발 키 선택은 별도 조건부 bean이 담당한다. */
    public record Jwt(
            @NotBlank String issuer,
            @NotBlank String audience,
            String activeKeyId,
            String privateKeyLocation,
            String publicKeysLocation,
            @NotNull Duration clockSkew) {
        /** 시간 오차 허용이 만료 검증을 무제한 완화하지 않게 한다. */
        @AssertTrue
        public boolean isClockSkewValid() {
            return clockSkew != null
                    && !clockSkew.isNegative()
                    && clockSkew.compareTo(Duration.ofSeconds(60)) <= 0;
        }
    }

    /** 실패 계정과 IP의 독립된 고정 window 한도다. */
    public record FailedLogin(
            @NotNull Duration window,
            @Min(1) int maxAttempts,
            @NotNull Duration lockDuration,
            @Min(1) int ipMaxAttempts,
            @NotNull Duration ipWindow) {
        /** 실패·IP window와 잠금 수명이 양수인지 확인한다. */
        @AssertTrue
        public boolean isWindowValid() {
            return positive(window) && positive(lockDuration) && positive(ipWindow);
        }
    }

    /** 외부 인증 진행 상태에만 사용하는 HttpSession 설정이다. */
    public record PreloginSession(
            boolean enabled, @NotNull Duration timeout, @NotBlank String namespace) {
        /** 선택 외부 로그인 세션은 최대 10분만 유지한다. */
        @AssertTrue
        public boolean isTimeoutValid() {
            return positive(timeout) && timeout.compareTo(Duration.ofMinutes(10)) <= 0;
        }
    }

    /** 쿠키 CSRF 토큰의 수명이다. */
    public record Csrf(@NotNull Duration maxAge) {
        /** CSRF 쿠키의 무제한 수명을 허용하지 않는다. */
        @AssertTrue
        public boolean isMaxAgeValid() {
            return positive(maxAge) && maxAge.compareTo(Duration.ofHours(8)) <= 0;
        }
    }

    /** 파일 출력과 local 상세 SQL의 독립 정책이다. */
    public record Logging(
            boolean fileEnabled,
            @NotBlank String directory,
            @NotBlank String maxFileSize,
            @Min(1) int maxHistory,
            @NotBlank String applicationTotalCap,
            @NotBlank String errorTotalCap,
            @Valid @NotNull Sql sql) {}

    /** 바인딩 원문을 포함하지 않는 mapper 호출 진단 설정이다. */
    public record Sql(
            boolean enabled, @NotNull Duration slowThreshold, @Min(1) @Max(16000) int maxChars) {
        /** slow 측정 임계치도 양수로 제한한다. */
        @AssertTrue
        public boolean isSlowThresholdValid() {
            return positive(slowThreshold);
        }
    }

    /** 보존과 조회 범위는 서로 다른 정책이다. 자동 대량 삭제를 활성화하지 않는다. */
    public record Audit(
            @Min(1) int retentionDays,
            @Valid @NotNull Query query,
            String loginReferenceKeyLocation) {}

    /** 감사 API에서 허용하는 최대 기간이다. */
    public record Query(@Min(1) @Max(31) int maxRangeDays) {}

    /** local 합성 데이터와 개발 키 사용 여부다. */
    public record Bootstrap(boolean localDataEnabled) {}

    /** 메모리 fallback 없이 한 저장소만 선택한다. */
    public enum StateStore {
        REDIS,
        JDBC
    }

    /** 제한 모드는 사용자당 한 로그인에 적용한다. */
    public enum ConcurrentLogin {
        ALLOW,
        DENY_NEW,
        REPLACE_OLD
    }

    /** 누락·0·음수 duration을 시작 검증에서 거부한다. */
    private static boolean positive(Duration value) {
        return value != null && !value.isNegative() && !value.isZero();
    }
}
