package com.example.starter.file.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

/** file part는 인증 후 지연 파싱하며 다른 multipart 형식은 받지 않는다. */
@Configuration(proxyBeanMethods = false)
public class FileWebConfiguration {
    /** DispatcherServlet에서 호출할 때까지 getParts를 실행하지 않는다. */
    @Bean
    public StandardServletMultipartResolver multipartResolver() {
        var resolver = new StandardServletMultipartResolver();
        resolver.setResolveLazily(true);
        resolver.setStrictServletCompliance(true);

        return resolver;
    }
}
