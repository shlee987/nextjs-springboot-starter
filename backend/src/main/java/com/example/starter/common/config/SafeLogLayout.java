package com.example.starter.common.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.LayoutBase;
import com.example.starter.common.web.UtcTimes;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;
import tools.jackson.databind.json.JsonMapper;

/** 메시지 원문을 관리하지 않는 외부 logger는 분류만 출력하고 Throwable는 직렬화하지 않는다. */
public class SafeLogLayout extends LayoutBase<ILoggingEvent> {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private boolean json;

    /** 공유 환경은 JSON, local은 UTC console 형식을 선택한다. */
    public void setJson(boolean json) {
        this.json = json;
    }

    /** MDC allowlist와 이미 정제한 서버 메시지만 한 이벤트에 담는다. */
    @Override
    public String doLayout(ILoggingEvent event) {
        String logger = event.getLoggerName();
        boolean owned = logger.startsWith("com.example.starter.") || logger.equals("starter.sql");
        String message =
                json && logger.equals("starter.sql")
                        ? "sql_detail_omitted"
                        : owned ? event.getFormattedMessage() : "framework_event";
        message = clean(message, logger.equals("starter.sql"));
        Map<String, String> mdc = event.getMDCPropertyMap();
        String trace = mdc.getOrDefault("traceId", "");
        String actor = mdc.getOrDefault("actorUserId", "");
        trace = trace.matches("[0-9a-f-]{36}") ? trace : "";
        actor = actor.matches("[1-9][0-9]{0,18}") ? actor : "";
        String timestamp = UtcTimes.formatMillis(Instant.ofEpochMilli(event.getTimeStamp()));
        String diagnostic = diagnostic(event.getThrowableProxy());

        if (json) {
            var fields = new LinkedHashMap<String, Object>();
            fields.put("timestamp", timestamp);
            fields.put("level", event.getLevel().toString());
            fields.put("traceId", trace);
            fields.put("actorUserId", actor.isEmpty() ? null : actor);
            fields.put("logger", logger);
            fields.put("message", message);

            if (!diagnostic.isEmpty()) {
                fields.put("diagnostic", diagnostic);
            }

            return JSON.writeValueAsString(fields) + System.lineSeparator();
        }

        if (logger.equals("org.springframework.boot.SpringApplication")
                || logger.equals(
                        "org.springframework.boot.diagnostics.LoggingFailureAnalysisReporter")) {
            return "";
        }
        String category =
                event.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.ERROR)
                        ? "ERROR"
                        : "APP";
        String localMessage;
        if (logger.equals("starter.lifecycle")) {
            String raw = event.getFormattedMessage();
            int separator = raw.indexOf(' ');
            category = separator < 0 ? "APP" : raw.substring(0, separator);
            localMessage = separator < 0 ? raw : raw.substring(separator + 1);
            if (category.equals("FAILED")) {
                category = "ERROR";
            }
        } else if (logger.equals("starter.sql")) {
            category = "SQL";
            localMessage = message;
        } else if (logger.endsWith(".RequestBoundaryFilter")) {
            category = "HTTP";
            var match =
                    Pattern.compile(
                                    "request_completed method=(\\w+) routeTemplate=(\\S+)"
                                            + " status=([0-9]+) elapsedMs=([0-9]+)")
                            .matcher(message);
            localMessage =
                    match.find()
                            ? match.group(1)
                                    + " "
                                    + match.group(2)
                                    + " → "
                                    + match.group(3)
                                    + " ("
                                    + match.group(4)
                                    + "ms)"
                            : "HTTP 요청 처리를 마쳤습니다.";
        } else if (logger.endsWith(".ProblemResponses")) {
            category = "ERROR";
            var match = Pattern.compile("code=([A-Z_]+)").matcher(message);
            localMessage = "요청 처리 실패" + (match.find() ? " (" + match.group(1) + ")" : "");
        } else if (owned && (message.startsWith("Starting ") || message.startsWith("Started "))) {
            category = "BOOT";
            localMessage =
                    message.startsWith("Starting ")
                            ? "Spring Boot를 시작합니다."
                            : "Spring 컨텍스트를 시작했습니다. 최종 준비를 확인합니다.";
        } else if (!owned) {
            category = frameworkCategory(logger);
            localMessage =
                    frameworkMessage(
                            logger, event.getFormattedMessage(), event.getLevel().toString());
        } else {
            localMessage = message;
        }
        String shortLogger = logger.substring(logger.lastIndexOf('.') + 1);
        String level = String.format("%-5s", event.getLevel());
        String label = "[" + category + "]";
        var color =
                switch (category) {
                    case "ERROR" -> AnsiColor.RED;
                    case "READY" -> AnsiColor.GREEN;
                    case "SQL" -> AnsiColor.CYAN;
                    case "START", "STOP", "BOOT", "DB", "CONNECT" -> AnsiColor.YELLOW;
                    default -> AnsiColor.DEFAULT;
                };
        String prefix =
                timestamp
                        + " "
                        + AnsiOutput.toString(color, level + " " + label, AnsiColor.DEFAULT);
        String request =
                trace.isEmpty()
                        ? ""
                        : " [trace=" + trace + (actor.isEmpty() ? "" : " user=" + actor) + "]";
        String safe = clean(localMessage, logger.equals("starter.sql"));
        int newline = safe.indexOf('\n');
        String body =
                newline < 0
                        ? safe + request
                        : safe.substring(0, newline) + request + safe.substring(newline);
        return prefix
                + " "
                + shortLogger
                + " — "
                + body
                + localDiagnostic(event.getThrowableProxy())
                + System.lineSeparator();
    }

    /** 외부 메시지는 원문 대신 알려진 작업 종류로 요약한다. */
    private String frameworkCategory(String logger) {
        if (logger.startsWith("com.zaxxer.hikari") || logger.startsWith("org.flywaydb")) {
            return "DB";
        }
        if (logger.startsWith("io.lettuce")) {
            return "CONNECT";
        }
        return "BOOT";
    }

    /** pool 이름·주소·자격·SQL을 메시지에서 추출하지 않는다. */
    private String frameworkMessage(String logger, String raw, String level) {
        if (logger.startsWith("com.zaxxer.hikari")) {
            if (raw.contains("Shutdown completed")) {
                return "DB 연결 풀을 종료했습니다.";
            }
            if (raw.contains("Shutdown initiated")) {
                return "DB 연결 풀을 종료합니다.";
            }
            if (raw.contains("Start completed")) {
                return "DB 연결 풀이 준비됐습니다.";
            }
            if (raw.contains("Added connection")) {
                return "DB 연결을 확인했습니다.";
            }
            if (raw.contains("Starting")) {
                return "DB 연결 풀을 시작합니다.";
            }
        }
        if (logger.startsWith("org.flywaydb")) {
            if (raw.startsWith("Successfully validated")) {
                return "마이그레이션 이력을 확인했습니다.";
            }
            if (raw.contains("up to date")) {
                return "DB 스키마가 최신 상태입니다.";
            }
            return "DB 마이그레이션 상태를 확인합니다.";
        }
        if (logger.startsWith("io.lettuce")) {
            return "Redis 연결 상태가 변경됐습니다. 오류가 있으면 아래 원인을 확인하세요.";
        }
        if (logger.startsWith("org.apache.coyote")
                || logger.contains("tomcat")
                || logger.contains("Tomcat")
                || logger.startsWith("org.apache.catalina")) {
            return raw.startsWith("Stopping") || raw.startsWith("Destroying")
                    ? "HTTP 서버를 종료합니다."
                    : "HTTP 서버 연결을 준비합니다.";
        }
        return level.equals("ERROR") ? "라이브러리 작업에 실패했습니다. 아래 원인을 확인하세요." : "프레임워크 상태 알림입니다.";
    }

    /** 원인 종류와 코드 위치만 여러 줄로 보여주고 원문 예외는 읽지 않는다. */
    private String localDiagnostic(ch.qos.logback.classic.spi.IThrowableProxy cause) {
        List<ch.qos.logback.classic.spi.IThrowableProxy> chain = new ArrayList<>();
        var seen =
                Collections.newSetFromMap(
                        new IdentityHashMap<ch.qos.logback.classic.spi.IThrowableProxy, Boolean>());
        while (cause != null && chain.size() < 32 && seen.add(cause)) {
            chain.add(cause);
            cause = cause.getCause();
        }
        Set<Integer> selected = new LinkedHashSet<>();
        if (!chain.isEmpty()) {
            selected.add(0);
        }
        if (chain.size() > 1) {
            selected.add(1);
        }
        if (chain.size() > 2) {
            selected.add(chain.size() - 2);
        }
        if (chain.size() > 1) {
            selected.add(chain.size() - 1);
        }
        StringBuilder text = new StringBuilder();
        for (int depth : selected) {
            var item = chain.get(depth);
            text.append("\n  ")
                    .append(
                            depth == 0
                                    ? "원인: "
                                    : depth == chain.size() - 1 ? "가장 안쪽 원인: " : "다음 원인: ")
                    .append(item.getClassName());
            if (item instanceof ch.qos.logback.classic.spi.ThrowableProxy proxy
                    && proxy.getThrowable() instanceof SQLException sql) {
                String state = sql.getSQLState();
                if (Set.of("28P01", "08001", "08006", "23505", "42601")
                        .contains(state == null ? "" : state)) {
                    text.append(" (SQLState=").append(state).append(')');
                }
            }
            var frames = item.getStackTraceElementProxyArray();
            if (frames != null) {
                for (int index = 0; index < Math.min(4, frames.length); index++) {
                    var frame = frames[index].getStackTraceElement();
                    text.append("\n    at ")
                            .append(frame.getClassName())
                            .append('.')
                            .append(frame.getMethodName())
                            .append(':')
                            .append(frame.getLineNumber());
                }
            }
        }
        return text.toString();
    }

    /** 기동 오류도 message/path 없이 원인 타입과 제한된 코드 위치만 남긴다. */
    private String diagnostic(ch.qos.logback.classic.spi.IThrowableProxy cause) {
        StringBuilder result = new StringBuilder();

        for (int depth = 0; cause != null && depth < 4; depth++, cause = cause.getCause()) {
            result.append(cause.getClassName()).append('[');
            var frames = cause.getStackTraceElementProxyArray();

            if (frames != null) {
                for (int index = 0; index < Math.min(8, frames.length); index++) {
                    var frame = frames[index].getStackTraceElement();
                    result.append(frame.getClassName())
                            .append('.')
                            .append(frame.getMethodName())
                            .append(':')
                            .append(frame.getLineNumber())
                            .append(';');
                }
            }

            result.append(']');
        }

        return result.toString();
    }

    /** 일반 로그의 줄 주입을 차단하고 local SQL의 줄바꿈만 보존한다. */
    private String clean(String message, boolean multiline) {
        StringBuilder result = new StringBuilder();

        for (int index = 0; index < message.length(); index++) {
            char value = message.charAt(index);
            result.append(
                    Character.isISOControl(value)
                                    && !(multiline && (value == '\n' || value == '\t'))
                            ? ' '
                            : value);
        }

        return result.toString();
    }
}
