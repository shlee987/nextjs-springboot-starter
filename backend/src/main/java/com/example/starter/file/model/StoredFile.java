package com.example.starter.file.model;

import java.time.Instant;

/** 경로 키와 상태를 포함하는 DB 전용 모델이며 공개 응답으로 사용하지 않는다. */
public record StoredFile(
        String fileId,
        long ownerUserId,
        String storageKey,
        String originalName,
        String contentType,
        long size,
        int width,
        int height,
        Instant createdAt,
        String status) {
    /** 원본명과 저장 키가 진단에 섞이지 않게 한다. */
    @Override
    public String toString() {
        return "StoredFile[redacted]";
    }
}
