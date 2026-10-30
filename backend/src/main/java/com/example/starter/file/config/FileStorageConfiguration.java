package com.example.starter.file.config;

import com.example.starter.file.storage.FileStore;
import com.example.starter.file.storage.LocalDiskFileStore;
import java.nio.file.Path;
import java.util.Arrays;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** 기본은 로컬 디스크이며 cloud SDK나 위장 검사기를 만들지 않는다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FileProperties.class)
public class FileStorageConfiguration {
    /** 공유 환경은 운영자가 지정한 절대 경로를 요구한다. */
    @Bean
    FileStore fileStore(FileProperties properties, Environment environment) {
        Path root = Path.of(properties.root());

        if (!Arrays.asList(environment.getActiveProfiles()).contains("local")
                && !root.isAbsolute()) {
            throw new IllegalStateException("Absolute external file root required");
        }

        return new LocalDiskFileStore(root);
    }
}
