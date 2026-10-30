package com.example.starter.common.config;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

/** local 시작과 모든 초기화가 끝난 실제 준비 완료를 구분한다. */
public final class LocalLifecycleLogging implements ApplicationListener<ApplicationEvent> {
    private static final Logger LOG = LoggerFactory.getLogger("starter.lifecycle");
    private final AtomicBoolean ready = new AtomicBoolean();
    private boolean failed;
    private ConfigurableApplicationContext context;
    private boolean local;

    /** ReadyEvent는 필수 초기화와 runner가 성공한 뒤에만 발생한다. */
    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ApplicationPreparedEvent prepared) {
            context = prepared.getApplicationContext();
            local = Arrays.asList(context.getEnvironment().getActiveProfiles()).contains("local");
            if (local) {
                LOG.info("START {} — local 환경에서 시작합니다.", name());
            }
        } else if (event instanceof ApplicationReadyEvent completed
                && local
                && completed.getApplicationContext() == context
                && !failed
                && ready.compareAndSet(false, true)) {
            if (context instanceof WebServerApplicationContext web && web.getWebServer() != null) {
                LOG.info(
                        "READY {} — local, 포트 {}, 준비 완료 ({}ms)",
                        name(),
                        web.getWebServer().getPort(),
                        completed.getTimeTaken().toMillis());
            } else {
                LOG.info(
                        "READY {} — local, 웹 포트 없음, 준비 완료 ({}ms)",
                        name(),
                        completed.getTimeTaken().toMillis());
            }
        } else if (event instanceof ApplicationFailedEvent failed && local) {
            this.failed = true;
            LOG.error("FAILED {} — 시작하지 못했습니다. 설정과 아래 원인을 확인하세요.", name(), failed.getException());
        } else if (event instanceof ContextClosedEvent closed
                && local
                && closed.getApplicationContext() == context) {
            LOG.info("STOP {} — 서버를 종료합니다.", name());
        }
    }

    /** 외부 설정이 로그 줄이나 비밀을 주입하지 않도록 앱 이름을 제한한다. */
    private String name() {
        String name =
                context.getEnvironment().getProperty("spring.application.name", "application");
        return name.matches("[A-Za-z0-9_.-]{1,64}") ? name : "application";
    }
}
