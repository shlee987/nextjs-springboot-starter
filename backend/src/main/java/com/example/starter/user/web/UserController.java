package com.example.starter.user.web;

import com.example.starter.common.web.ApiIds;
import com.example.starter.common.web.ApiResponse;
import com.example.starter.common.web.ApiResponses;
import com.example.starter.common.web.ListQuery;
import com.example.starter.common.web.PageData;
import com.example.starter.user.model.UserViews;
import com.example.starter.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 조회 입력 검증과 typed 성공 반환만 수행한다. */
@RestController
@RequestMapping(value = "/api/users", produces = "application/json")
public class UserController {
    private final UserService service;

    /** 권한·감사 판단은 서비스에 둔다. */
    public UserController(UserService service) {
        this.service = service;
    }

    /** 본인 프로필은 인증된 주체에서만 결정한다. */
    @GetMapping("/me")
    @Operation(
            operationId = "getMyProfile",
            summary = "본인 기본 프로필",
            description = "USER_READ_SELF 필요. 다른 actorId 입력을 받지 않습니다.")
    public ResponseEntity<ApiResponse<UserViews.Profile>> self() {
        return ApiResponses.ok(service.self());
    }

    /** 안정 정렬 목록과 동일 조건 count를 반환한다. */
    @GetMapping
    @Operation(
            operationId = "searchUsers",
            summary = "사용자 목록",
            description = "USER_READ, REQUIRED 감사. q 최대100, size 최대100, 503 감사 장애 시 본문을 반환하지 않습니다.")
    public ResponseEntity<ApiResponse<PageData<UserViews.Summary>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "idAsc") ListQuery.Sort sort) {
        return ApiResponses.ok(service.search(new ListQuery(q, page, size, sort)));
    }

    /** 숫자 문자열 키와 별도 개인정보 권한을 요구한다. */
    @GetMapping("/{userId}")
    @Operation(
            operationId = "getUser",
            summary = "사용자 상세",
            description =
                    "USER_READ와 PERSONAL_INFO_READ, REQUIRED 감사. 404 대상 없음, 403 권한 부족, 503 이력 실패.")
    public ResponseEntity<ApiResponse<UserViews.Profile>> detail(@PathVariable String userId) {
        return ApiResponses.ok(service.detail(ApiIds.parse(userId)));
    }
}
