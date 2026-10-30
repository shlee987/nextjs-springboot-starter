package com.example.starter.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

/** START와 모든 초기화 후 READY, 실패와 shared 무출력을 구분한다. */
class LocalLifecycleLoggingTest {
    /** READY는 실제 웹 서버 포트를 사용하고 중복 이벤트에도 한 번만 표시한다. */
    @Test
    void readyUsesActualPortAndIsEmittedExactlyOnceAfterPrepared() {
        var logger = (Logger) LoggerFactory.getLogger("starter.lifecycle");
        var captured = new ListAppender<ILoggingEvent>();
        captured.start();
        logger.addAppender(captured);
        try (var context = new WebContext()) {
            context.getEnvironment().setActiveProfiles("local");
            var app = new SpringApplication();
            var listener = new LocalLifecycleLogging();
            listener.onApplicationEvent(new ApplicationPreparedEvent(app, new String[0], context));
            assertEquals(1, captured.list.size());
            assertTrue(captured.list.getFirst().getFormattedMessage().startsWith("START "));
            var ready =
                    new ApplicationReadyEvent(app, new String[0], context, Duration.ofMillis(3250));
            listener.onApplicationEvent(ready);
            listener.onApplicationEvent(ready);
            assertEquals(2, captured.list.size());
            String text = captured.list.getLast().getFormattedMessage();
            assertTrue(text.startsWith("READY "));
            assertTrue(text.contains("포트 19086"));
            assertTrue(text.contains("3250ms"));
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }

    /** 실패한 시작에는 READY가 없고 공유 환경에는 local 안내를 출력하지 않는다. */
    @Test
    void failureNeverProducesReadyAndSharedKeepsExistingLogging() {
        var logger = (Logger) LoggerFactory.getLogger("starter.lifecycle");
        var captured = new ListAppender<ILoggingEvent>();
        captured.start();
        logger.addAppender(captured);
        try (var context = new WebContext()) {
            context.getEnvironment().setActiveProfiles("local");
            var app = new SpringApplication();
            var listener = new LocalLifecycleLogging();
            listener.onApplicationEvent(new ApplicationPreparedEvent(app, new String[0], context));
            listener.onApplicationEvent(
                    new ApplicationFailedEvent(
                            app,
                            new String[0],
                            context,
                            new IllegalStateException("SYNTHETIC_SECRET")));
            listener.onApplicationEvent(
                    new ApplicationReadyEvent(app, new String[0], context, Duration.ofMillis(1)));
            assertTrue(
                    captured.list.stream()
                            .noneMatch(event -> event.getFormattedMessage().startsWith("READY ")));
            var layout = new SafeLogLayout();
            String text = layout.doLayout(captured.list.getLast());
            assertTrue(text.contains("[ERROR]"));
            assertFalse(text.contains("SYNTHETIC_SECRET"));
            int previous = captured.list.size();
            context.getEnvironment().setActiveProfiles("prod");
            var shared = new LocalLifecycleLogging();
            shared.onApplicationEvent(new ApplicationPreparedEvent(app, new String[0], context));
            shared.onApplicationEvent(
                    new ApplicationReadyEvent(app, new String[0], context, Duration.ofMillis(1)));
            assertEquals(previous, captured.list.size());
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }

    /** 실제 소켓 없이 Boot가 제공하는 웹 서버 포트 조회 방식을 검사한다. */
    static class WebContext extends GenericApplicationContext
            implements WebServerApplicationContext {
        /** 설정 포트가 아니라 실제 WebServer가 확인한 포트를 돌려준다. */
        @Override
        public WebServer getWebServer() {
            return new WebServer() {
                public void start() {}

                public void stop() {}

                public int getPort() {
                    return 19086;
                }
            };
        }

        /** 루트 웹 문맥의 namespace다. */
        @Override
        public String getServerNamespace() {
            return null;
        }
    }
}
