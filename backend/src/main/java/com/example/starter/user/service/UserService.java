package com.example.starter.user.service;

import com.example.starter.audit.service.AccessAudit;
import com.example.starter.auth.model.AuthContext;
import com.example.starter.auth.model.AuthPermissions;
import com.example.starter.common.web.ApiError;
import com.example.starter.common.web.ApiException;
import com.example.starter.common.web.ListQuery;
import com.example.starter.common.web.PageData;
import com.example.starter.permission.Permission;
import com.example.starter.user.mapper.UserMapper;
import com.example.starter.user.model.UserAccount;
import com.example.starter.user.model.UserDepartment;
import com.example.starter.user.model.UserViews;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 사용자 공개 projection과 등록 권한·인증 상태의 조합을 소유한다. */
@Service
public class UserService {
    private final UserMapper mapper;
    private final JsonMapper json;

    /** DB 조회와 내부 permission snapshot parser를 주입한다. */
    public UserService(UserMapper mapper, JsonMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    /** 검증된 본인의 현재 표시 정보를 반환한다. */
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public UserViews.CurrentUser current() {
        var login = AuthContext.require();

        return current(login.userId(), AuthPermissions.parse(json, login.permissionsJson()));
    }

    /** 로그인 발급 경계에서도 동일한 비밀 없는 projection을 사용한다. */
    public UserViews.CurrentUser current(long userId, List<String> permissions) {
        UserAccount user = required(userId);
        var departments =
                mapper.selectDepartmentList(List.of(userId)).stream()
                        .map(
                                row ->
                                        new UserViews.Membership(
                                                row.departmentId(),
                                                row.departmentName(),
                                                row.primary()))
                        .toList();

        return new UserViews.CurrentUser(
                Long.toString(userId),
                user.loginId(),
                user.displayName(),
                departments,
                mapper.selectRoleList(userId),
                Permission.registered(permissions));
    }

    /** 본인 이외의 요청 actor ID로 조회 범위를 바꾸지 않는다. */
    @PreAuthorize("hasAuthority('USER_READ_SELF')")
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public UserViews.Profile self() {
        long userId = AuthContext.require().userId();

        return profile(required(userId), mapper.selectDepartmentList(List.of(userId)));
    }

    /** 개인정보 상세는 추가 권한과 필수 접근 이력을 요구한다. */
    @PreAuthorize("hasAuthority('USER_READ') and hasAuthority('PERSONAL_INFO_READ')")
    @AccessAudit(
            resource = AccessAudit.Resource.USER_DETAIL,
            action = AccessAudit.Action.READ,
            policy = AccessAudit.Policy.REQUIRED)
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public UserViews.Profile detail(long userId) {
        return profile(required(userId), mapper.selectDepartmentList(List.of(userId)));
    }

    /** 목록과 count는 같은 조건이며 소속은 페이지 전체를 batch 조회한다. */
    @PreAuthorize("hasAuthority('USER_READ')")
    @AccessAudit(
            resource = AccessAudit.Resource.USER_LIST,
            action = AccessAudit.Action.SEARCH,
            policy = AccessAudit.Policy.REQUIRED)
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public PageData<UserViews.Summary> search(ListQuery query) {
        var users = mapper.selectUserList(query);
        var departments =
                users.isEmpty()
                        ? List.<UserDepartment>of()
                        : mapper.selectDepartmentList(
                                users.stream().map(UserAccount::userId).toList());
        var items =
                users.stream()
                        .map(
                                user ->
                                        new UserViews.Summary(
                                                Long.toString(user.userId()),
                                                user.displayName(),
                                                user.status(),
                                                departments(user.userId(), departments)))
                        .toList();

        return PageData.of(items, query.page(), query.size(), mapper.selectUserCount(query));
    }

    /** 요청 범위에 사용자가 없으면 공개 NOT_FOUND를 사용한다. */
    private UserAccount required(long userId) {
        UserAccount user = mapper.selectAccount(userId);

        if (user == null) {
            throw new ApiException(ApiError.NOT_FOUND);
        }

        return user;
    }

    /** 내부 credential 필드는 projection에 복사하지 않는다. */
    private UserViews.Profile profile(UserAccount user, List<UserDepartment> rows) {
        return new UserViews.Profile(
                Long.toString(user.userId()),
                user.displayName(),
                user.status(),
                departments(user.userId(), rows),
                user.loginId(),
                user.email(),
                user.createdAt(),
                user.updatedAt());
    }

    /** 공개 소속에는 다른 사용자 ID와 대표 여부를 섞지 않는다. */
    private List<UserViews.Department> departments(long userId, List<UserDepartment> rows) {
        return rows.stream()
                .filter(row -> row.userId() == userId)
                .map(row -> new UserViews.Department(row.departmentId(), row.departmentName()))
                .toList();
    }
}
