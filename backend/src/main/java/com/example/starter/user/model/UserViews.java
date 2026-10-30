package com.example.starter.user.model;

import java.time.Instant;
import java.util.List;

/** 비밀 자격과 영속 모델을 포함하지 않는 사용자 공개 projection이다. */
public final class UserViews {
    /** 공개 DTO namespace의 인스턴스 생성을 막는다. */
    private UserViews() {}

    /** 로그인 상태에 표시할 내부 사용자와 권한이다. */
    public record CurrentUser(
            String userId,
            String loginId,
            String displayName,
            List<Membership> departments,
            List<String> roles,
            List<String> permissions) {}

    /** 대표 여부를 포함한 본인의 소속이다. */
    public record Membership(String departmentId, String departmentName, boolean primary) {}

    /** 목록에 필요한 최소 소속이다. */
    public record Department(String departmentId, String departmentName) {}

    /** 개인정보 권한 없이 읽는 사용자 목록이다. */
    public record Summary(
            String userId, String displayName, String status, List<Department> departments) {}

    /** 별도 개인정보 권한으로 읽는 상세 프로필이다. */
    public record Profile(
            String userId,
            String displayName,
            String status,
            List<Department> departments,
            String loginId,
            String email,
            Instant createdAt,
            Instant updatedAt) {}
}
