package com.example.starter.auth.service;

import com.example.starter.audit.model.AuditError;
import com.example.starter.audit.service.AuditWriter;
import com.example.starter.auth.model.AuthContext;
import com.example.starter.auth.model.AuthError;
import com.example.starter.auth.store.AuthStateStore;
import com.example.starter.common.config.AppProperties;
import com.example.starter.common.web.ApiError;
import com.example.starter.common.web.ApiException;
import com.example.starter.common.web.UtcTimes;
import com.example.starter.department.mapper.DepartmentMapper;
import com.example.starter.permission.Permission;
import com.example.starter.user.mapper.UserMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 계정 변경은 JDBC 단일 commit 또는 Redis 영속 fence workflow만 통과한다. */
@Service
public class AuthInvalidationService {
    private static final Logger LOG = LoggerFactory.getLogger(AuthInvalidationService.class);
    private final UserMapper users;
    private final DepartmentMapper departments;
    private final AuthStateStore states;
    private final AuditWriter audit;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final boolean externalState;

    /** 저장소 분기는 이 정합성 경계에만 모으고 개별 업무에 흩뿌리지 않는다. */
    public AuthInvalidationService(
            UserMapper users,
            DepartmentMapper departments,
            AuthStateStore states,
            AuditWriter audit,
            Clock clock,
            PlatformTransactionManager manager,
            AppProperties properties) {
        this.users = users;
        this.departments = departments;
        this.states = states;
        this.audit = audit;
        this.clock = clock;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setTimeout(5);
        this.externalState = properties.auth().stateStore() == AppProperties.StateStore.REDIS;
    }

    /** 비활성화는 해당 사용자의 모든 로그인과 같은 변경 경계를 사용한다. */
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public void disableAccount(long userId, long version) {
        change(
                List.of(userId),
                null,
                "ACCOUNT_DISABLED",
                () -> {
                    if (users.selectAccount(userId).version() != version) {
                        throw new ApiException(ApiError.CONFLICT);
                    }
                },
                now -> {
                    if (users.updateStatus(userId, "INACTIVE", version, AuthContext.actor(), now)
                            != 1) {
                        throw new ApiException(ApiError.CONFLICT);
                    }
                });
    }

    /** 본인의 현재 해시 검증과 새 해시 계산은 호출자가 잠금 밖에서 완료한다. */
    public void changePassword(long userId, String expectedHash, String newHash) {
        if (AuthContext.require().userId() != userId) {
            throw new ApiException(ApiError.ACCESS_DENIED);
        }

        change(
                List.of(userId),
                null,
                "PASSWORD_CHANGED",
                () -> {
                    var latest = users.selectAccount(userId);

                    if (latest == null
                            || !"ACTIVE".equals(latest.status())
                            || !expectedHash.equals(latest.passwordHash())) {
                        throw new ApiException(ApiError.CONFLICT);
                    }
                },
                now -> {
                    if (users.updatePassword(userId, newHash, now) != 1) {
                        throw new ApiException(AuthError.PASSWORD_LOGIN_UNAVAILABLE);
                    }
                });
    }

    /** 역할 행을 먼저 잠가 권한 변경의 영향 사용자 열거와 경쟁하지 않는다. */
    @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
    public void assignRole(long userId, long roleId, boolean assigned) {
        change(
                List.of(userId),
                roleId,
                "ROLE_CHANGED",
                () -> {},
                now -> {
                    if (assigned) {
                        users.insertUserRole(userId, roleId, AuthContext.actor(), now);
                    } else {
                        users.deleteUserRole(userId, roleId);
                    }
                });
    }

    /** 잠근 역할의 현재 사용자 집합 전체를 폐기하며 ADMIN에도 명시 권한만 부여한다. */
    @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
    public void replaceRolePermissions(long roleId, List<String> permissions) {
        List<String> registered;

        try {
            registered = Permission.registered(permissions);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ApiError.VALIDATION_FAILED);
        }

        change(
                null,
                roleId,
                "ROLE_CHANGED",
                () -> {},
                now -> {
                    users.deleteRolePermissions(roleId);

                    for (String permission : registered) {
                        if (users.insertRolePermission(roleId, permission, AuthContext.actor(), now)
                                != 1) {
                            throw new ApiException(ApiError.CONFLICT);
                        }
                    }
                });
    }

    /** 소속 이동도 현재 로그인 표시·권한 snapshot을 폐기하고 재로그인하게 한다. */
    @PreAuthorize("hasAuthority('DEPARTMENT_UPDATE')")
    public void moveUserDepartments(long userId, List<Long> departmentIds, Long primaryId) {
        var ids = departmentIds.stream().distinct().sorted().toList();

        if (ids.size() != departmentIds.size() || primaryId != null && !ids.contains(primaryId)) {
            throw new ApiException(ApiError.VALIDATION_FAILED);
        }

        change(
                List.of(userId),
                null,
                "ROLE_CHANGED",
                () -> {
                    var existing = departments.selectDepartmentListForUpdate();

                    if (!existing.containsAll(ids)) {
                        throw new ApiException(ApiError.NOT_FOUND);
                    }

                    for (long id : ids) {
                        if (!departments.selectActive(id)) {
                            throw new ApiException(ApiError.CONFLICT);
                        }
                    }
                },
                now -> {
                    users.deleteUserDepartments(userId);

                    for (long departmentId : ids) {
                        if (users.insertUserDepartment(
                                        userId,
                                        departmentId,
                                        Long.valueOf(departmentId).equals(primaryId),
                                        AuthContext.actor(),
                                        now)
                                != 1) {
                            throw new ApiException(ApiError.CONFLICT);
                        }
                    }
                });
    }

    /** Redis의 중간 실패는 fence를 남긴다. 복구 식별자는 진단에만 기록한다. */
    private void change(
            List<Long> explicitUsers,
            Long roleId,
            String event,
            Runnable validation,
            Consumer<Instant> mutation) {
        String operation = UUID.randomUUID().toString();
        Instant now = UtcTimes.millis(clock.instant());
        audit.access("ACCOUNT_SECURITY", "UPDATE", null, "STARTED", null, operation);

        AtomicBoolean fenceStarted = new AtomicBoolean();
        AtomicBoolean committed = new AtomicBoolean();

        try {
            List<Long> affected =
                    transaction.execute(
                            status -> {
                                if (roleId != null && users.selectRoleForUpdate(roleId) == null) {
                                    throw new ApiException(ApiError.NOT_FOUND);
                                }

                                List<Long> ids =
                                        explicitUsers == null
                                                ? users.selectRoleUserList(roleId)
                                                : explicitUsers.stream()
                                                        .distinct()
                                                        .sorted()
                                                        .toList();

                                for (long id : ids) {
                                    if (users.selectUserForUpdate(id) == null) {
                                        throw new ApiException(ApiError.NOT_FOUND);
                                    }
                                }

                                validation.run();

                                for (long id : ids) {
                                    fenceStarted.set(true);
                                    states.block(id, operation, now.toEpochMilli());
                                }

                                mutation.accept(now);

                                for (long id : ids) {
                                    audit.securityInTransaction(event, id);

                                    if (!externalState) {
                                        states.revokeAll(id, now.toEpochMilli(), event);
                                        states.unblock(id, operation, now.toEpochMilli());
                                    }
                                }

                                audit.accessInTransaction("ACCOUNT_SECURITY", operation);

                                return ids;
                            });

            committed.set(true);

            if (externalState) {
                // 전체 폐기가 모두 확인된 뒤에만 해제를 시작한다.
                for (long id : affected) {
                    states.revokeAll(id, now.toEpochMilli(), event);
                }

                for (long id : affected) {
                    states.unblock(id, operation, now.toEpochMilli());
                }
            }

        } catch (RuntimeException failure) {
            boolean knownRollback = failure instanceof ApiException && !committed.get();

            if (fenceStarted.get() && externalState) {
                LOG.error(
                        "auth_change_requires_verification operationId={} dbCommitConfirmed={}",
                        operation,
                        committed.get());
            }

            try {
                audit.access(
                        "ACCOUNT_SECURITY",
                        "UPDATE",
                        null,
                        knownRollback ? "FAILURE" : "UNKNOWN",
                        "CHANGE_UNCONFIRMED",
                        operation);
            } catch (RuntimeException auditFailure) {
                failure.addSuppressed(auditFailure);
                throw new ApiException(AuditError.AUDIT_UNAVAILABLE, auditFailure);
            }

            if (failure instanceof ApiException api && (!externalState || !fenceStarted.get())) {
                throw api;
            }

            throw new ApiException(AuthError.AUTH_STATE_UNAVAILABLE, failure);
        }
    }
}
