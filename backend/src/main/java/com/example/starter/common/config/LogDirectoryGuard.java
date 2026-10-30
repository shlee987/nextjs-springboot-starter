package com.example.starter.common.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/** 파일 appender 초기화 전에 경로 쓰기 실패를 고정 console ERROR로 알리고 시작을 중단한다. */
public final class LogDirectoryGuard implements EnvironmentPostProcessor, Ordered {
    /** 환경 정책 검증 뒤 LoggingSystem 초기화 전에 실행한다. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 12;
    }

    /** local 콘솔 모드에는 파일을 만들지 않고 shared 경로만 짧게 probe한다. */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication app) {
        if (!environment.getProperty("app.logging.file-enabled", Boolean.class, false)) {
            return;
        }

        try {
            String serverId = environment.getRequiredProperty("app.server-id");

            if (!serverId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")) {
                throw new IllegalArgumentException("Invalid server ID");
            }

            Path directory =
                    Path.of(
                            environment.getRequiredProperty("app.logging.directory"),
                            environment.getActiveProfiles()[0],
                            serverId);
            Files.createDirectories(directory);
            Files.delete(Files.createTempFile(directory, "readiness-", ".tmp"));
        } catch (Exception failure) {
            System.err.println("ERROR log_directory_unavailable");
            throw new IllegalStateException("Log directory configuration failed");
        }
    }
}
