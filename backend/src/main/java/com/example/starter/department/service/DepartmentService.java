package com.example.starter.department.service;

import com.example.starter.audit.service.AccessAudit;
import com.example.starter.auth.model.AuthContext;
import com.example.starter.common.web.ApiError;
import com.example.starter.common.web.ApiException;
import com.example.starter.common.web.ListQuery;
import com.example.starter.common.web.PageData;
import com.example.starter.common.web.UtcTimes;
import com.example.starter.department.mapper.DepartmentMapper;
import com.example.starter.department.model.DepartmentSummary;
import java.time.Clock;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 부서 검색과 잠금 아래의 간접 순환 방지를 담당한다. */
@Service
public class DepartmentService {
    private final DepartmentMapper mapper;
    private final Clock clock;

    /** 계층 SQL과 UTC 생성 시각을 주입한다. */
    public DepartmentService(DepartmentMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    /** 등록된 권한으로만 부서 목록을 읽는다. */
    @PreAuthorize("hasAuthority('DEPARTMENT_READ')")
    @AccessAudit(resource = AccessAudit.Resource.DEPARTMENT, action = AccessAudit.Action.SEARCH)
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public PageData<DepartmentSummary> search(ListQuery query) {
        return PageData.of(
                mapper.selectDepartmentList(query),
                query.page(),
                query.size(),
                mapper.selectDepartmentCount(query));
    }

    /** 전체 계층 ID 순서 잠금 후 조상을 확인하여 교차 변경 순환을 차단한다. */
    @PreAuthorize("hasAuthority('DEPARTMENT_UPDATE')")
    @AccessAudit(resource = AccessAudit.Resource.DEPARTMENT, action = AccessAudit.Action.UPDATE)
    @Transactional(rollbackFor = Exception.class)
    public void changeParent(long departmentId, Long parentId, long version) {
        var ids = mapper.selectDepartmentListForUpdate();

        if (!ids.contains(departmentId) || parentId != null && !ids.contains(parentId)) {
            throw new ApiException(ApiError.NOT_FOUND);
        }

        if (parentId != null && mapper.selectAncestorList(parentId).contains(departmentId)) {
            throw new ApiException(ApiError.CONFLICT);
        }

        if (mapper.updateParent(
                        departmentId,
                        parentId,
                        version,
                        AuthContext.actor(),
                        UtcTimes.millis(clock.instant()))
                != 1) {
            throw new ApiException(ApiError.CONFLICT);
        }
    }
}
