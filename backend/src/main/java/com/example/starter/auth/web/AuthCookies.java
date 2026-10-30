package com.example.starter.auth.web;

import com.example.starter.auth.model.IssuedTokens;
import com.example.starter.common.web.ApiError;
import com.example.starter.common.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** raw 자격은 HttpOnly 쿠키로만 출력한다. 401에서는 삭제하지 않는다. */
@Component
public class AuthCookies {
    private final String prefix;
    private final boolean secure;
    private final Clock clock;

    /** 보안 속성은 시작 시 검증된 파생 설정만 사용한다. */
    public AuthCookies(Environment environment, Clock clock) {
        this.prefix = environment.getRequiredProperty("app.cookies.prefix");
        this.secure = environment.getRequiredProperty("app.cookies.secure", Boolean.class);
        this.clock = clock;
    }

    /** JWT exp가 아닌 절대 로그인 수명까지 쿠키를 보존한다. */
    public void issue(HttpServletResponse response, IssuedTokens tokens) {
        long seconds =
                Math.max(
                        0,
                        Duration.between(clock.instant(), tokens.absoluteExpiresAt()).getSeconds());
        cookie(response, "access", tokens.access(), seconds);
        cookie(response, "refresh", tokens.refresh(), seconds);
    }

    /** 명시 logout/비밀번호 변경에서 같은 Path와 속성으로 삭제한다. */
    public void clear(HttpServletResponse response) {
        cookie(response, "access", "", 0);
        cookie(response, "refresh", "", 0);
    }

    /** 동일 이름 중복 쿠키를 임의 선택하지 않는다. */
    public String read(HttpServletRequest request, String type) {
        String value = null;

        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if ((prefix + type).equals(cookie.getName())) {
                    if (value != null) {
                        throw new ApiException(ApiError.MALFORMED_REQUEST);
                    }

                    value = cookie.getValue();
                }
            }
        }

        return value;
    }

    /** 보안 구성에서 동일 쿠키명을 사용한다. */
    public String name(String type) {
        return prefix + type;
    }

    /** local loopback HTTP만 Secure 예외를 사용한다. */
    public boolean secure() {
        return secure;
    }

    /** Domain 없는 host-only 쿠키를 출력한다. */
    private void cookie(HttpServletResponse response, String name, String value, long seconds) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(prefix + name, value)
                        .httpOnly(true)
                        .secure(secure)
                        .sameSite("Lax")
                        .path("/")
                        .maxAge(seconds)
                        .build()
                        .toString());
    }
}
