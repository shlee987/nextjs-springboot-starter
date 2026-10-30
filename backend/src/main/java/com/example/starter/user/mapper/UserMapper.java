package com.example.starter.user.mapper;

import com.example.starter.common.web.ListQuery;
import com.example.starter.user.model.UserAccount;
import com.example.starter.user.model.UserDepartment;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 사용자·권한 데이터를 같은 트랜잭션 연결에서 읽고 변경한다. */
@Mapper
public interface UserMapper {
    /** 허용된 로그인 ID로 비밀 검증용 모델을 읽는다. */
    UserAccount selectAccountByLoginId(String loginId);

    /** 내부 사용자 키로 기본 모델을 읽는다. */
    UserAccount selectAccount(long userId);

    /** 발급·계정 변경 경쟁을 사용자 DB 행에서 직렬화한다. */
    Long selectUserForUpdate(long userId);

    /** 검색/정렬 허용 enum으로 페이지를 읽는다. */
    List<UserAccount> selectUserList(ListQuery query);

    /** 목록과 동일 검색 조건의 건수를 읽는다. */
    long selectUserCount(ListQuery query);

    /** 조회한 사용자들의 소속을 한 번에 읽는다. */
    List<UserDepartment> selectDepartmentList(@Param("userIds") List<Long> userIds);

    /** 활성 역할 코드만 결정적인 순서로 읽는다. */
    List<String> selectRoleList(long userId);

    /** 배포 허용목록의 활성 역할 권한만 읽는다. */
    List<String> selectPermissionList(long userId);

    /** 계정 상태와 version을 조건 변경한다. */
    int updateStatus(
            @Param("userId") long userId,
            @Param("status") String status,
            @Param("version") long version,
            @Param("actor") Long actor,
            @Param("now") Instant now);

    /** 기존 credential만 변경하고 임의로 자격을 생성하지 않는다. */
    int updatePassword(
            @Param("userId") long userId, @Param("hash") String hash, @Param("now") Instant now);

    /** 역할 변경을 잠그며 사용자 잠금보다 먼저 획득한다. */
    Long selectRoleForUpdate(long roleId);

    /** 권한 변경 영향 사용자를 오름차순으로 읽는다. */
    List<Long> selectRoleUserList(long roleId);

    /** 역할 할당 연결을 명시 추가한다. */
    int insertUserRole(
            @Param("userId") long userId,
            @Param("roleId") long roleId,
            @Param("actor") Long actor,
            @Param("now") Instant now);

    /** 역할 할당을 명시 제거한다. */
    int deleteUserRole(@Param("userId") long userId, @Param("roleId") long roleId);

    /** 역할의 기존 권한 연결을 명시 제거한다. */
    int deleteRolePermissions(long roleId);

    /** 등록된 권한만 역할에 연결한다. */
    int insertRolePermission(
            @Param("roleId") long roleId,
            @Param("code") String code,
            @Param("actor") Long actor,
            @Param("now") Instant now);

    /** 사용자 소속을 명시 교체할 때 기존 연결을 제거한다. */
    int deleteUserDepartments(long userId);

    /** 유효 부서에 새 소속과 대표 여부를 기록한다. */
    int insertUserDepartment(
            @Param("userId") long userId,
            @Param("departmentId") long departmentId,
            @Param("primary") boolean primary,
            @Param("actor") Long actor,
            @Param("now") Instant now);
}
