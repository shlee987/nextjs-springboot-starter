package com.example.starter.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.example.starter.TestPolicies;
import com.example.starter.TestTransactions;
import com.example.starter.audit.mapper.AuditMapper;
import com.example.starter.audit.model.AuditEvent;
import com.example.starter.audit.model.AuditViews;
import com.example.starter.common.config.SafeLogLayout;
import com.example.starter.common.web.ProblemResponses;
import com.example.starter.common.web.RequestBoundaryFilter;
import com.example.starter.common.web.RequestContext;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** 직접 peer 영속과 HTTP 없는 SYSTEM NULL, 공개/진단 allowlist를 검사한다. */
class AuditPeerContractTest {
    /** Dispatcher 이전 필터에서도 정규화 peer가 양 이력에 저장되고 위조 헤더는 무시된다. */
    @Test
    void bothAuditsUseNormalizedDirectIpv4AndIpv6AndSystemHasNoPeer() throws Exception {
        List<AuditEvent> events = new ArrayList<>();
        var json = JsonMapper.builder().build();
        var writer = writer(events);
        var filter =
                new RequestBoundaryFilter(
                        new ProblemResponses(json), TestPolicies.environment(), null, null, null);
        for (String peer : List.of("192.0.2.10", "2001:db8::10")) {
            var request = new MockHttpServletRequest("GET", "/synthetic");
            request.setRemoteAddr(peer);
            request.addHeader("Forwarded", "for=203.0.113.7");
            request.addHeader("X-Forwarded-For", "203.0.113.8");
            String expected = RequestContext.peer(request);
            int offset = events.size();
            filter.doFilter(
                    request,
                    new MockHttpServletResponse(),
                    (req, res) -> {
                        writer.access("USER_LIST", "READ", null, "SUCCESS", null, null);
                        writer.security("LOGIN_FAILED", null, "FAILURE", "LOGIN_FAILED");
                    });
            for (AuditEvent event : events.subList(offset, events.size())) {
                assertEquals(expected, event.clientIp());
                assertFalse(event.toString().contains(expected));
                verifySql(event);
            }
            assertNull(RequestContext.currentPeer());
        }
        writer.access("USER_LIST", "READ", null, "SUCCESS", null, null);
        writer.security("LOGIN_FAILED", null, "FAILURE", "LOGIN_FAILED");
        for (AuditEvent event : events.subList(4, 6)) {
            assertNull(event.clientIp());
            assertEquals("SYSTEM", event.actorKind());
        }
    }

    /** 영속 필드는 공개 DTO/SELECT와 진단 MDC 출력에 추가되지 않는다. */
    @Test
    void publicProjectionsAndDiagnosticLogsExcludeRawPeer() throws Exception {
        for (Class<?> view : List.of(AuditViews.Access.class, AuditViews.Security.class)) {
            assertTrue(
                    Arrays.stream(view.getRecordComponents())
                            .noneMatch(field -> field.getName().equals("clientIp")));
        }
        String xml =
                new ClassPathResource("mappers/audit/AuditMapper.xml")
                        .getContentAsString(StandardCharsets.UTF_8);
        assertFalse(
                xml.substring(xml.indexOf("<sql id=\"accessCondition\">")).contains("CLIENT_IP"));
        var event = new LoggingEvent();
        event.setLoggerName("com.example.starter.audit.Test");
        event.setLevel(Level.INFO);
        event.setMessage("audit completed");
        event.setMDCPropertyMap(Map.of("clientIp", "192.0.2.10"));
        var layout = new SafeLogLayout();
        layout.setJson(true);
        assertFalse(layout.doLayout(event).contains("192.0.2.10"));
    }

    /** 실제 MyBatis가 두 INSERT의 inet parameter를 바인딩하고 원문은 SQL에 넣지 않는다. */
    private void verifySql(AuditEvent event) throws Exception {
        var configuration = new Configuration();
        try (var input = new ClassPathResource("mappers/audit/AuditMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input, configuration, "audit", configuration.getSqlFragments())
                    .parse();
        }
        for (String name : List.of("insertAccess", "insertSecurity")) {
            var bound =
                    configuration
                            .getMappedStatement(
                                    "com.example.starter.audit.mapper.AuditMapper." + name)
                            .getBoundSql(event);
            assertTrue(bound.getSql().contains("\"CLIENT_IP\""));
            assertTrue(bound.getSql().contains("?::inet"));
            assertTrue(
                    bound.getParameterMappings().stream()
                            .anyMatch(parameter -> parameter.getProperty().equals("clientIp")));
            assertFalse(bound.getSql().contains(event.clientIp()));
        }
    }

    /** INSERT 관찰만 수행하며 실제 연결/이력 쓰기를 하지 않는다. */
    private AuditWriter writer(List<AuditEvent> events) {
        var mapper =
                (AuditMapper)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {AuditMapper.class},
                                (target, method, args) -> {
                                    events.add((AuditEvent) args[0]);
                                    return 1;
                                });
        return new AuditWriter(
                mapper,
                Clock.systemUTC(),
                TestPolicies.local(),
                new TestTransactions(),
                TestPolicies.references());
    }
}
