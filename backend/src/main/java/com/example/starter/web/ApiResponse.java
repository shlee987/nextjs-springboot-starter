package com.example.starter.web;

/** 공개 업무 결과와 서버 요청 식별자만 반환한다. */
public record ApiResponse<T>(T data, String traceId) {
}
