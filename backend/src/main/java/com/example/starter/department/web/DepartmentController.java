package com.example.starter.department.web;

import com.example.starter.common.web.ApiResponse;
import com.example.starter.common.web.ApiResponses;
import com.example.starter.common.web.ListQuery;
import com.example.starter.common.web.PageData;
import com.example.starter.department.model.DepartmentSummary;
import com.example.starter.department.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리 CRUD 없이 최소 부서 조회만 공개한다. */
@RestController
@RequestMapping(produces = "application/json")
public class DepartmentController {
    private final DepartmentService service;

    /** 권한과 데이터 접근 판단은 서비스로 연결한다. */
    public DepartmentController(DepartmentService service) {
        this.service = service;
    }

    /** 고정 enum 정렬로 부서를 검색한다. */
    @GetMapping("/api/departments")
    @Operation(
            operationId = "searchDepartments",
            summary = "부서 목록",
            description = "DEPARTMENT_READ 필요. q 최대100, page 0 기반, size 1~100, BEST_EFFORT 접근 이력.")
    public ResponseEntity<ApiResponse<PageData<DepartmentSummary>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "idAsc") ListQuery.Sort sort) {
        return ApiResponses.ok(service.search(new ListQuery(q, page, size, sort)));
    }
}
