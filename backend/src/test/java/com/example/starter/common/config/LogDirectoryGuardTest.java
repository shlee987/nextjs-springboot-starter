package com.example.starter.common.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

/** 파일 경로 probe만 임시 폴더에 실행하며 DB/Redis/Application은 시작하지 않는다. */
class LogDirectoryGuardTest {
    @TempDir Path temporary;

    /** local console 설정에는 파일을 만들지 않는다. */
    @Test
    void localConsoleDoesNotCreateFiles() {
        Path target = temporary.resolve("local-output");
        var environment =
                new MockEnvironment()
                        .withProperty("app.logging.file-enabled", "false")
                        .withProperty("app.logging.directory", target.toString());
        environment.setActiveProfiles("local");
        new LogDirectoryGuard().postProcessEnvironment(environment, new SpringApplication());
        assertFalse(Files.exists(target));
    }

    /** shared node별 폴더를 검사하고 probe 파일을 남기지 않는다. */
    @Test
    void sharedDirectoryProbeIsScopedAndRemoved() throws Exception {
        var environment =
                new MockEnvironment()
                        .withProperty("app.logging.file-enabled", "true")
                        .withProperty("app.logging.directory", temporary.toString())
                        .withProperty("app.server-id", "test-node");
        environment.setActiveProfiles("dev");
        new LogDirectoryGuard().postProcessEnvironment(environment, new SpringApplication());
        Path target = temporary.resolve("dev/test-node");
        assertTrue(Files.isDirectory(target));

        try (var entries = Files.list(target)) {
            assertTrue(entries.findAny().isEmpty());
        }
    }
}
