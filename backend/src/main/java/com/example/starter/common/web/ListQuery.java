package com.example.starter.common.web;

/** 목록의 상한·검색 escape·정렬 허용값을 데이터 접근 전에 확정한다. */
public record ListQuery(String q, int page, int size, Sort sort) {
    /** 자동 trim 없이 검색 길이와 페이지 경계를 검증한다. */
    public ListQuery {
        if (page < 0 || size < 1 || size > 100 || q != null && q.length() > 100 || sort == null) {
            throw new ApiException(ApiError.VALIDATION_FAILED);
        }
    }

    /** int 곱셈 overflow 없이 SQL offset을 계산한다. */
    public long offset() {
        return Math.multiplyExact((long) page, size);
    }

    /** PostgreSQL ESCAPE '\'에 맞게 검색 메타 문자를 문자로 취급한다. */
    public String pattern() {
        return q == null
                ? null
                : "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** 외부 문자열은 이 enum 외 SQL로 전달하지 않는다. */
    public enum Sort {
        idAsc,
        nameAsc,
        nameDesc
    }
}
