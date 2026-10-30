package com.example.starter.permission;

import java.util.Collection;
import java.util.List;

/** 배포 SQL과 함께 관리하는 권한 허용목록이다. ADMIN 우회는 제공하지 않는다. */
public enum Permission {
    USER_READ_SELF,
    USER_READ,
    PERSONAL_INFO_READ,
    USER_UPDATE,
    DEPARTMENT_READ,
    DEPARTMENT_UPDATE,
    ROLE_READ,
    ROLE_ASSIGN,
    AUDIT_READ,
    SECURITY_AUDIT_READ;

    /** 미등록 DB 코드가 JWT 인증 권한으로 확장되지 않게 한다. */
    public static List<String> registered(Collection<String> codes) {
        return codes.stream().map(Permission::valueOf).map(Enum::name).distinct().sorted().toList();
    }
}
