package com.example.starter.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.ansi.AnsiOutput.Enabled;
import tools.jackson.databind.json.JsonMapper;

/** 출력 layout의 UTC·allowlist·원문 예외/외부 message 누출을 검사한다. */
class SafeLogContractTest {
    /** 공유 JSON은 MDC allowlist만 포함하고 Throwable와 library raw message를 버린다. */
    @Test
    void sharedJsonUsesSafeAllowlist() {
        var layout = new SafeLogLayout();
        layout.setJson(true);
        var event =
                event(
                        "org.example.framework",
                        "SYNTHETIC_PASSWORD_MARKER\r\nforged",
                        Map.of(
                                "traceId",
                                "b5cf43c1-9585-43cd-b637-e458bb487956",
                                "actorUserId",
                                "9223372036854775807",
                                "refresh",
                                "SYNTHETIC_REFRESH_MARKER"));
        event.setThrowableProxy(new ThrowableProxy(new RuntimeException("SYNTHETIC_CAUSE_MARKER")));
        String output = layout.doLayout(event);
        assertFalse(output.contains("SYNTHETIC"));
        var json = JsonMapper.builder().build().readTree(output);
        assertEquals("2026-10-29 14:30:00.123", json.get("timestamp").asString());
        assertEquals(7, json.size());
        assertTrue(json.get("diagnostic").asString().contains("RuntimeException"));
        assertEquals("9223372036854775807", json.get("actorUserId").asString());
    }

    /** local SQL의 placeholder와 줄바꿈을 유지하되 일반 메시지는 줄 주입을 차단한다. */
    @Test
    void localSqlKeepsLayoutAndOtherMessagesCannotForgeLines() {
        var layout = new SafeLogLayout();
        var sql = layout.doLayout(event("starter.sql", "SQL:\nSELECT\n    ?"));
        assertTrue(sql.contains("SQL:\nSELECT\n    ?"));
        var ordinary = layout.doLayout(event("com.example.starter.Test", "safe\r\nforged"));
        assertFalse(ordinary.contains("safe\r\nforged"));
    }

    /** 색상 유무에 관계없이 분류가 보이고 요청이 없으면 빈 문맥을 반복하지 않는다. */
    @Test
    void localCategoriesColorAndFrameworkSummariesRemainSafe() {
        var layout = new SafeLogLayout();
        try {
            AnsiOutput.setEnabled(Enabled.NEVER);
            String ready =
                    layout.doLayout(
                            event(
                                    "starter.lifecycle",
                                    "READY starter-api — local, 포트 18086, 준비 완료 (100ms)"));
            assertTrue(ready.contains("[READY]"));
            assertFalse(ready.contains("traceId="));
            assertFalse(ready.contains("actorUserId="));
            assertFalse(ready.contains("\u001b"));
            AnsiOutput.setEnabled(Enabled.ALWAYS);
            assertTrue(
                    layout.doLayout(event("starter.lifecycle", "READY safe")).contains("\u001b["));
            String pool =
                    layout.doLayout(
                            event(
                                    "com.zaxxer.hikari.HikariDataSource",
                                    "SYNTHETIC_DB_CREDENTIAL - Start completed."));
            assertTrue(pool.contains("DB 연결 풀이 준비됐습니다"));
            assertFalse(pool.contains("SYNTHETIC"));
            var error = event("org.example.driver", "SYNTHETIC_PASSWORD");
            error.setThrowableProxy(
                    new ThrowableProxy(new IllegalStateException("SYNTHETIC_SQL_PASSWORD")));
            String failure = layout.doLayout(error);
            assertTrue(failure.contains("\n  원인: java.lang.IllegalStateException\n    at "));
            assertFalse(failure.contains("SYNTHETIC"));
            var shared = new SafeLogLayout();
            shared.setJson(true);
            String output =
                    shared.doLayout(event("starter.sql", "SELECT SYNTHETIC_SQL PARAMS secret"));
            assertTrue(output.contains("sql_detail_omitted"));
            assertFalse(output.contains("SYNTHETIC"));
            assertFalse(output.contains("\u001b"));
        } finally {
            AnsiOutput.setEnabled(Enabled.DETECT);
        }
    }

    /** 깊은 Spring 포장 예외에도 가장 안쪽 DB 원인과 안전한 상태 코드가 보인다. */
    @Test
    void localErrorsIncludeDeepestCauseWithoutSqlOrCredentials() {
        Throwable failure = new SQLException("SYNTHETIC_SQL_PASSWORD", "28P01");
        for (int index = 0; index < 10; index++) {
            failure = new IllegalStateException("SYNTHETIC_WRAPPER", failure);
        }
        var entry = event("com.example.starter.Test", "safe failure");
        entry.setThrowableProxy(new ThrowableProxy(failure));
        String output = new SafeLogLayout().doLayout(entry);
        assertTrue(output.contains("가장 안쪽 원인: java.sql.SQLException (SQLState=28P01)"));
        assertFalse(output.contains("SYNTHETIC"));
    }

    /** 실서버 없이 Logback의 실제 이벤트 모델을 만든다. */
    private LoggingEvent event(String logger, String message) {
        return event(logger, message, Map.of());
    }

    /** MDC 속성은 실제 Logback 이벤트와 같이 한 번만 설정한다. */
    private LoggingEvent event(String logger, String message, Map<String, String> mdc) {
        var event = new LoggingEvent();
        event.setLoggerName(logger);
        event.setLevel(Level.INFO);
        event.setMessage(message);
        event.setTimeStamp(Instant.parse("2026-10-29T14:30:00.123Z").toEpochMilli());
        event.setMDCPropertyMap(mdc);

        return event;
    }
}
