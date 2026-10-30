package com.example.starter.common.web;

import java.util.List;

/** 0 기반 페이지와 동일 검색 조건의 전체 건수를 표현한다. */
public record PageData<T>(List<T> items, int page, int size, long totalElements, long totalPages) {
    /** 페이지 범위를 검증하고 외부 목록 변경으로 결과가 달라지지 않게 복사한다. */
    public PageData {
        if (page < 0
                || size < 1
                || size > 100
                || totalElements < 0
                || totalElements > 9_007_199_254_740_991L
                || totalPages != totalElements / size + (totalElements % size == 0 ? 0 : 1)) {
            throw new ApiException(ApiError.MALFORMED_REQUEST);
        }

        items = List.copyOf(items);
    }

    /** 전체 건수에서 올림 계산한 페이지 정보를 구성한다. */
    public static <T> PageData<T> of(List<T> items, int page, int size, long totalElements) {
        if (size < 1 || size > 100) {
            throw new ApiException(ApiError.MALFORMED_REQUEST);
        }

        return new PageData<>(
                items,
                page,
                size,
                totalElements,
                totalElements / size + (totalElements % size == 0 ? 0 : 1));
    }
}
