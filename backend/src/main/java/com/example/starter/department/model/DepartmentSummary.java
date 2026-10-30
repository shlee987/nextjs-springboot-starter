package com.example.starter.department.model;

/** bigint 키를 문자열로 공개하는 부서 조회 모델이다. */
public record DepartmentSummary(
        String departmentId,
        String departmentCode,
        String departmentName,
        String parentId,
        int sortOrder,
        boolean active) {}
