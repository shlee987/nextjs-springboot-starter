package com.example.starter.file.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 저장 루트만 외부 설정으로 받고 HTTP 입력으로는 받지 않는다. */
@Validated
@ConfigurationProperties("app.files")
public record FileProperties(@NotBlank String root) {}
