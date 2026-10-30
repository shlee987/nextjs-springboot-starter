package com.example.starter.department.mapper;

import com.example.starter.common.web.ListQuery;
import com.example.starter.department.model.DepartmentSummary;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 부서 목록과 계층 변경용 잠금을 제공한다. */
@Mapper
public interface DepartmentMapper {
    /** 잠근 부서가 신규 소속을 받을 수 있는지 확인한다. */
    boolean selectActive(long departmentId);

    /** 동일 검색·정렬 계약으로 부서 페이지를 읽는다. */
    List<DepartmentSummary> selectDepartmentList(ListQuery query);

    /** 목록과 같은 검색 조건의 건수다. */
    long selectDepartmentCount(ListQuery query);

    /** 조상 변경 경쟁을 막기 위해 부서 계층을 ID 순서로 잠근다. */
    List<Long> selectDepartmentListForUpdate();

    /** 현재 계층에서 부모 방향의 조상 ID를 읽는다. */
    List<Long> selectAncestorList(long departmentId);

    /** version 조건으로 새 부모를 연결한다. */
    int updateParent(
            @Param("departmentId") long departmentId,
            @Param("parentId") Long parentId,
            @Param("version") long version,
            @Param("actor") Long actor,
            @Param("now") Instant now);
}
