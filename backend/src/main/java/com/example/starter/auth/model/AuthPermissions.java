package com.example.starter.auth.model;

import com.example.starter.common.web.ApiException;
import com.example.starter.permission.Permission;
import java.util.Arrays;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/** 저장소 권한 snapshot만 읽으며 손상/미등록 코드를 인증 성공으로 완화하지 않는다. */
public final class AuthPermissions {
    /** snapshot 도구의 인스턴스 생성을 막는다. */
    private AuthPermissions() {}

    /** 저장소 JSON 오류는 권한 부족403과 구분한 상태 장애503이다. */
    public static List<String> parse(JsonMapper json, String snapshot) {
        try {
            return Permission.registered(Arrays.asList(json.readValue(snapshot, String[].class)));
        } catch (RuntimeException failure) {
            throw new ApiException(AuthError.AUTH_STATE_UNAVAILABLE, failure);
        }
    }
}
