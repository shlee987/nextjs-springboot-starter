package com.example.starter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/** 외부 PostgreSQL과 Redis를 사용하는 API 실행 진입점이다. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class StarterApplication {
    /** 명시한 배포 프로파일과 외부 설정으로 서버를 시작한다. */
    public static void main(String[] args) {
        SpringApplication.run(StarterApplication.class, args);
    }
}
