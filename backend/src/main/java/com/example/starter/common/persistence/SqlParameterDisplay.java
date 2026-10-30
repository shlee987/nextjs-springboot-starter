package com.example.starter.common.persistence;

import com.example.starter.audit.model.AuditEvent;
import com.example.starter.audit.model.AuditQuery;
import com.example.starter.common.web.ListQuery;
import com.example.starter.common.web.UtcTimes;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.session.Configuration;

/** 실행용 객체를 바꾸지 않고 명시적으로 허용한 바인딩 값만 표시한다. */
final class SqlParameterDisplay {
    private static final Set<String> NUMBERS =
            Set.of(
                    "id",
                    "page",
                    "size",
                    "offset",
                    "userId",
                    "userIds",
                    "departmentId",
                    "departmentIds",
                    "parentId",
                    "primaryId",
                    "roleId",
                    "roleIds",
                    "actorUserId",
                    "actorId",
                    "actor",
                    "targetUserId",
                    "version");
    private static final Set<String> DATES =
            Set.of("from", "to", "now", "occurredAt", "cutoff", "expiresAt");
    private static final Map<String, Set<String>> CODES =
            Map.of(
                    "status", Set.of("ACTIVE", "INACTIVE"),
                    "outcome", Set.of("STARTED", "SUCCESS", "FAILURE", "UNKNOWN", "DENIED"),
                    "resourceCode",
                            Set.of(
                                    "USER_LIST",
                                    "USER_DETAIL",
                                    "DEPARTMENT",
                                    "ACCESS_AUDIT",
                                    "SECURITY_AUDIT",
                                    "ACCOUNT_SECURITY"),
                    "actionCode", Set.of("READ", "SEARCH", "CREATE", "UPDATE", "DELETE", "EXPORT"));

    /** 정적 표시 도구의 인스턴스 생성을 막는다. */
    private SqlParameterDisplay() {}

    /** SQL을 치환하지 않고 JDBC 바인딩 순서대로 이름·타입·안전한 값을 표시한다. */
    static String format(Configuration configuration, BoundSql bound, int maximum) {
        StringBuilder text = new StringBuilder("  SQL:\n");
        text.append(escape(bound.getSql(), true, maximum)).append("\n  PARAMS:");
        var mappings = bound.getParameterMappings();
        if (mappings == null || mappings.isEmpty()) {
            text.append(" (없음)");
        } else {
            int count = Math.min(mappings.size(), 32);
            for (int index = 0; index < count && text.length() < maximum; index++) {
                ParameterMapping mapping = mappings.get(index);
                String name = mapping.getProperty();
                String displayedName =
                        name != null
                                        && name.matches(
                                                "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z0-9_$]+|\\[[0-9]+\\])*")
                                        && name.length() <= 96
                                ? name
                                : "[PARAMETER]";
                String type =
                        mapping.getJavaType() == null
                                ? "Object"
                                : mapping.getJavaType().getSimpleName();
                String jdbc = mapping.getJdbcType() == null ? "AUTO" : mapping.getJdbcType().name();
                text.append("\n    ")
                        .append(index + 1)
                        .append(". ")
                        .append(displayedName)
                        .append(" (")
                        .append(escape(type, false, 64))
                        .append('/')
                        .append(jdbc)
                        .append(") = ");
                text.append(display(configuration, bound, mapping));
            }
            if (mappings.size() > count) {
                text.append("\n    [PARAMS_TRUNCATED]");
            }
        }
        if (text.length() > maximum) {
            return text.substring(0, maximum) + "\n  [DETAIL_TRUNCATED]";
        }
        return text.toString();
    }

    /** 인증/키 테이블은 SQL 자체와 모든 값을 표시하지 않는다. */
    static boolean sensitive(String statementId, String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        return statementId.contains(".auth.")
                || upper.contains("LOCAL_CREDENTIAL")
                || upper.contains("AUTH_")
                || upper.contains("STARTER_LOCAL_ONLY")
                || upper.contains("DEV_SIGNING_KEY");
    }

    /** 미허용 속성은 getter/값/toString을 읽기 전에 가린다. */
    private static String display(
            Configuration configuration, BoundSql bound, ParameterMapping mapping) {
        if (mapping.getMode() == ParameterMode.OUT) {
            return "[OUT]";
        }
        String name = mapping.getProperty();
        String leaf = name == null ? "" : name.substring(name.lastIndexOf('.') + 1);
        if (leaf.startsWith("__frch_")) {
            leaf = leaf.substring(7).replaceFirst("_[0-9]+$", "");
        }
        if (!NUMBERS.contains(leaf) && !DATES.contains(leaf) && !CODES.containsKey(leaf)) {
            return "[MASKED]";
        }
        try {
            Object parameter = bound.getParameterObject();
            Object value;
            if (bound.hasAdditionalParameter(name)) {
                value = read(bound.getAdditionalParameters(), name);
            } else if (parameter == null) {
                value = null;
            } else if (configuration
                    .getTypeHandlerRegistry()
                    .hasTypeHandler(parameter.getClass())) {
                value = parameter;
            } else {
                value = read(parameter, name);
            }
            if (value == null) {
                return "null";
            }
            if (NUMBERS.contains(leaf)) {
                if (value instanceof Byte
                        || value instanceof Short
                        || value instanceof Integer
                        || value instanceof Long) {
                    return value.toString();
                }
                if (value instanceof BigInteger integer
                        && value.getClass() == BigInteger.class
                        && integer.bitLength() <= 64) {
                    return integer.toString();
                }
            }
            if (DATES.contains(leaf)) {
                if (value instanceof Instant instant) {
                    return UtcTimes.formatMillis(instant);
                }
                if (value instanceof LocalDate date) {
                    return date.toString();
                }
                if (value instanceof LocalDateTime date) {
                    return date.toString();
                }
            }
            if (value instanceof String code && CODES.getOrDefault(leaf, Set.of()).contains(code)) {
                return escape(code, false, 128);
            }
            return "[MASKED]";
        } catch (RuntimeException unavailable) {
            return "[UNAVAILABLE]";
        }
    }

    /** getter를 다시 호출해 바인딩 결과를 바꾸지 않고 알려진 불변 DTO/Map만 읽는다. */
    private static Object read(Object root, String path) {
        Object value = root;
        for (String part : path.split("\\.")) {
            if (value == null) {
                return null;
            }
            if (value instanceof ListQuery page) {
                value =
                        switch (part) {
                            case "page" -> page.page();
                            case "size" -> page.size();
                            case "offset" -> page.offset();
                            default -> throw new IllegalArgumentException("Not a display field");
                        };
            } else if (value instanceof AuditQuery query) {
                value =
                        switch (part) {
                            case "from" -> query.from();
                            case "to" -> query.to();
                            case "page" -> query.page();
                            case "actorId" -> query.actorId();
                            case "targetUserId" -> query.targetUserId();
                            case "resourceCode" -> query.resourceCode();
                            default -> throw new IllegalArgumentException("Not a display field");
                        };
            } else if (value instanceof AuditEvent event) {
                value =
                        switch (part) {
                            case "actorUserId" -> event.actorUserId();
                            case "targetUserId" -> event.targetUserId();
                            case "occurredAt" -> event.occurredAt();
                            case "outcome" -> event.outcome();
                            case "resourceCode" -> event.resourceCode();
                            case "actionCode" -> event.actionCode();
                            default -> throw new IllegalArgumentException("Not a display field");
                        };
            } else if (value instanceof Map<?, ?> map
                    && (value.getClass() == HashMap.class
                            || value.getClass() == LinkedHashMap.class
                            || value.getClass() == ParamMap.class
                            || value.getClass()
                                    .getName()
                                    .startsWith("java.util.ImmutableCollections$"))) {
                value = map.get(part);
            } else {
                throw new IllegalArgumentException("Unsupported diagnostic value");
            }
        }
        return value;
    }

    /** SQL 줄바꿈만 보존하고 나머지 제어문자는 보이는 escape로 바꾼다. */
    static String escape(String value, boolean multiline, int maximum) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < value.length() && text.length() < maximum; index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character)
                    && !(multiline && (character == '\n' || character == '\t'))) {
                text.append(String.format("\\u%04x", (int) character));
            } else {
                text.append(character);
            }
        }
        return text.toString();
    }
}
