package com.example.starter.user.model;

/** DB 소속 행이다. 사용자 목록을 batch 조립할 때 사용한다. */
public record UserDepartment(
        long userId, String departmentId, String departmentName, boolean primary) {}
