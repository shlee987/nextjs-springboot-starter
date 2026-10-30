package com.example.starter.file.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 소유자에게만 반환하는 재인코딩 완료 파일 정보다. */
public record FileInfo(
        @Schema(format = "uuid") String fileId,
        @Schema(maxLength = 120) String originalName,
        @Schema(allowableValues = {"image/png", "image/jpeg"}) String contentType,
        @Schema(minimum = "1", maximum = "5242880", description = "재인코딩 후 저장된 바이트 수") long size,
        @Schema(minimum = "1", maximum = "8192") int width,
        @Schema(minimum = "1", maximum = "8192") int height,
        Instant createdAt) {
    /** 내부 저장 키와 상태는 공개하지 않는다. */
    public static FileInfo from(StoredFile file) {
        return new FileInfo(
                file.fileId(),
                file.originalName(),
                file.contentType(),
                file.size(),
                file.width(),
                file.height(),
                file.createdAt());
    }
}
