package com.example.starter.auth.web;

import com.example.starter.auth.model.AuthPermissions;
import com.example.starter.auth.model.IssuedTokens;
import com.example.starter.auth.service.AuthLoginService;
import com.example.starter.common.web.ApiResponse;
import com.example.starter.common.web.ApiResponses;
import com.example.starter.common.web.RequestContext;
import com.example.starter.user.model.UserViews;
import com.example.starter.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/** 자격 원문은 Set-Cookie에만 쓰고 공개 API에는 표시용 사용자·시각만 반환한다. */
@RestController
@RequestMapping(value = "/api/auth", produces = "application/json")
public class AuthController {
    private final AuthLoginService auth;
    private final UserService users;
    private final AuthCookies cookies;
    private final CookieCsrfTokenRepository csrf;
    private final JsonMapper json;

    /** Controller는 Mapper를 직접 호출하지 않는다. */
    public AuthController(
            AuthLoginService auth,
            UserService users,
            AuthCookies cookies,
            CookieCsrfTokenRepository csrf,
            JsonMapper json) {
        this.auth = auth;
        this.users = users;
        this.cookies = cookies;
        this.csrf = csrf;
        this.json = json;
    }

    /** XOR 표현을 materialize해 프론트가 동일 resolver의 header로 보내게 한다. */
    @GetMapping("/csrf")
    @Operation(
            operationId = "getCsrf",
            summary = "미인증 CSRF 토큰 조회",
            description = "IP 한도 적용. no-store. X-CSRF-TOKEN으로 제출하며 HttpOnly 쿠키 원문을 읽지 않습니다.")
    public ResponseEntity<ApiResponse<CsrfView>> csrf(HttpServletRequest request) {
        var token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());

        return ApiResponses.ok(new CsrfView(token.getToken(), token.getHeaderName()));
    }

    /** 검증 후 쿠키로만 발급하고 로그인 경계에서 CSRF를 교체한다. */
    @PostMapping("/login")
    @Operation(
            operationId = "login",
            summary = "자체 비밀번호 로그인",
            description =
                    "Origin/CSRF 필수. 401 LOGIN_FAILED, 409 CONCURRENT_LOGIN_DENIED, 429"
                        + " RATE_LIMITED, 503 저장소/이력 장애. JWT/refresh는 HttpOnly Set-Cookie 전용입니다.")
    public ResponseEntity<ApiResponse<LoginView>> login(
            @Valid @RequestBody AuthRequests.Login body,
            HttpServletRequest request,
            HttpServletResponse response) {
        var prepared =
                auth.login(
                        body,
                        RequestContext.peer(request),
                        tokens ->
                                new PreparedLogin(
                                        tokens,
                                        users.current(
                                                tokens.userId(),
                                                AuthPermissions.parse(
                                                        json, tokens.permissionsJson()))));
        cookies.issue(response, prepared.tokens());
        csrf.saveToken(csrf.generateToken(request), request, response);

        return ApiResponses.ok(new LoginView(prepared.user(), prepared.tokens().accessExpiresAt()));
    }

    /** refresh만으로 갱신하며 JWT 만료·활동 시간을 바꾸지 않는다. */
    @PostMapping("/refresh")
    @Operation(
            operationId = "refresh",
            summary = "단회 갱신 자격 회전",
            description =
                    "Origin/CSRF 필수. 사용한 hash 재사용은 해당 sid 폐기 및 401 TOKEN_REFRESH_REUSED. 응답 유실 시"
                            + " 재로그인. 503 발급 실패는 부분 자격을 폐기합니다.")
    public ResponseEntity<ApiResponse<RefreshView>> refresh(
            HttpServletRequest request, HttpServletResponse response) {
        var tokens = auth.refresh(cookies.read(request, "refresh"));
        cookies.issue(response, tokens);

        return ApiResponses.ok(
                new RefreshView(tokens.accessExpiresAt(), tokens.absoluteExpiresAt()));
    }

    /** 유효 자격 부재도 204이며 명시 logout만 쿠키를 삭제한다. */
    @PostMapping("/logout")
    @Operation(
            operationId = "logout",
            summary = "현재 로그인 폐기",
            description =
                    "Origin/CSRF 필수. 만료 access가 앞에서 거부되지 않습니다. 폐기는 이력 실패에도 유지되며 이력 장애는 503입니다.")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(cookies.read(request, "refresh"), cookies.read(request, "access"));
        cookies.clear(response);
        csrf.saveToken(csrf.generateToken(request), request, response);

        return ApiResponses.noContent();
    }

    /** 모든 기기의 sid를 폐기하며 인증된 userId만 사용한다. */
    @PostMapping("/logout-all")
    @Operation(
            operationId = "logoutAll",
            summary = "본인 전체 로그인 폐기",
            description = "유효 access, Origin/CSRF 필요. 모든 서버에서 다음 요청부터 거부합니다.")
    public ResponseEntity<Void> logoutAll(
            HttpServletRequest request, HttpServletResponse response) {
        auth.logoutAll();
        cookies.clear(response);
        csrf.saveToken(csrf.generateToken(request), request, response);

        return ApiResponses.noContent();
    }

    /** 표시용 본인 정보 조회는 활동 요청이며 익명 200을 허용하지 않는다. */
    @GetMapping("/me")
    @Operation(
            operationId = "getCurrentUser",
            summary = "현재 인증 사용자",
            description = "유효 access와 공유 sid 필요. 활동을 연장하므로 상태 폴링으로 사용하지 않습니다.")
    public ResponseEntity<ApiResponse<UserViews.CurrentUser>> me() {
        return ApiResponses.ok(users.current());
    }

    /** 다중 탭 refresh 잠금 후 상태 확인은 본문과 활동 연장이 없다. */
    @RequestMapping(value = "/me", method = RequestMethod.HEAD)
    @Operation(operationId = "headCurrentUser", summary = "활동 연장 없는 인증 상태 확인")
    public ResponseEntity<Void> head() {
        return ResponseEntity.ok().build();
    }

    /** credential 변경 commit과 전체 폐기 확인 뒤 재로그인하게 한다. */
    @PutMapping("/password")
    @Operation(
            operationId = "changePassword",
            summary = "자체 비밀번호 변경",
            description =
                    "유효 access, Origin/CSRF 필요. 400 현재값 오류/동일값, 409 자체 credential 미보유, 503 이력/fence"
                            + " 장애. 모든 로그인 폐기와 재로그인이 필요합니다.")
    public ResponseEntity<Void> password(
            @Valid @RequestBody AuthRequests.Password body,
            HttpServletRequest request,
            HttpServletResponse response) {
        auth.password(body);
        cookies.clear(response);
        csrf.saveToken(csrf.generateToken(request), request, response);

        return ApiResponses.noContent();
    }

    /** 응답 조회가 성공한 뒤 쿠키 경계로 전달하는 내부 결과다. */
    private record PreparedLogin(IssuedTokens tokens, UserViews.CurrentUser user) {}

    /** 공개 XOR token과 header 이름이다. */
    public record CsrfView(String token, String headerName) {}

    /** 자격 원문 없이 발급 완료와 사용자 정보만 공개한다. */
    public record LoginView(UserViews.CurrentUser user, Instant accessExpiresAt) {}

    /** 갱신 결과의 UTC 만료 시각이다. */
    public record RefreshView(Instant accessExpiresAt, Instant absoluteExpiresAt) {}
}
