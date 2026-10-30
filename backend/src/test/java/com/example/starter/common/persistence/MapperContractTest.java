package com.example.starter.common.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.common.web.ApiException;
import com.example.starter.common.web.ListQuery;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** XML을 실제 MyBatis로 파싱하며 DB 실행/자동 매핑 왕복을 통과한 것으로 주장하지 않는다. */
class MapperContractTest {
    /** 각 sort 분기·LIKE escape·count 공통 조건이 값 바인딩을 유지한다. */
    @Test
    void dynamicSqlUsesOnlyServerSortAndPlaceholders() throws Exception {
        Configuration configuration = configuration();

        for (String prefix : new String[] {"user.UserMapper", "department.DepartmentMapper"}) {
            String name = prefix.startsWith("user") ? "User" : "Department";
            String namespace = "com.example.starter." + prefix.replace(".", ".mapper.");

            for (var sort : ListQuery.Sort.values()) {
                var input = new ListQuery("SYNTHETIC_SECRET_%' OR 1=1--", 2, 20, sort);
                var sql =
                        configuration
                                .getMappedStatement(namespace + ".select" + name + "List")
                                .getBoundSql(input);
                String text = sql.getSql();
                assertFalse(text.contains("SYNTHETIC_SECRET"));
                assertTrue(text.contains("ILIKE ? ESCAPE"));
                assertTrue(text.contains("LIMIT ? OFFSET ?"));
                assertTrue(
                        text.contains(
                                "\""
                                        + (name.equals("User") ? "USER_ID" : "DEPARTMENT_ID")
                                        + "\" ASC"));
                assertTrue(text.contains("\n"));
                var meta = configuration.newMetaObject(input);
                assertEquals(40L, meta.getValue("offset"));
                assertEquals("%SYNTHETIC\\_SECRET\\_\\%' OR 1=1--%", input.pattern());
                var count =
                        configuration
                                .getMappedStatement(namespace + ".select" + name + "Count")
                                .getBoundSql(input);
                assertTrue(count.getSql().contains("ILIKE ? ESCAPE"));
                assertFalse(count.getSql().contains("ORDER BY"));
            }
        }
    }

    /** inactive repository SQL이 XML 등록만으로 실행되지 않으며 모든 Mapper가 파싱된다. */
    @Test
    void allMappersParseAndAuditIsAppendOnly() throws Exception {
        Configuration configuration = configuration();
        assertTrue(
                configuration
                        .getMappedStatementNames()
                        .contains("com.example.starter.auth.store.AuthStateMapper.updateRefresh"));

        for (String name : configuration.getMappedStatementNames()) {
            if (name.startsWith("com.example.starter.audit.mapper.")) {
                var type = configuration.getMappedStatement(name).getSqlCommandType().name();
                assertTrue(type.equals("SELECT") || type.equals("INSERT"));
            }
        }

        var sql =
                configuration
                        .getMappedStatement(
                                "com.example.starter.auth.store.AuthStateMapper.deleteExpiredLogins")
                        .getBoundSql(Map.of("now", 1L))
                        .getSql();
        assertTrue(sql.contains("SKIP LOCKED"));
        assertTrue(sql.contains("\"ABSOLUTE_EXPIRES_AT\""));
        assertFalse(sql.contains("IDLE_EXPIRES_AT"));
    }

    /** 파일 조회·변경은 소유자를 바인딩하며 정렬과 상태 전이는 서버가 결정한다. */
    @Test
    void fileQueriesBindOwnershipAndReserveQuotaConditionally() throws Exception {
        var configuration = configuration();
        String namespace = "com.example.starter.file.mapper.FileMapper.";
        var parameters =
                Map.of(
                        "fileId",
                        "synthetic-id",
                        "ownerUserId",
                        7,
                        "size",
                        3,
                        "offset",
                        0,
                        "status",
                        "DELETING");
        for (String method :
                new String[] {
                    "selectOwned",
                    "selectList",
                    "count",
                    "ready",
                    "deleting",
                    "delete",
                    "reserve",
                    "release"
                }) {
            var statement =
                    configuration.getMappedStatement(namespace + method).getBoundSql(parameters);
            assertFalse(statement.getSql().contains("synthetic-id"));
            assertTrue(
                    statement.getParameterMappings().stream()
                            .anyMatch(value -> value.getProperty().equals("ownerUserId")),
                    method);
        }
        String reserve =
                configuration
                        .getMappedStatement(namespace + "reserve")
                        .getBoundSql(parameters)
                        .getSql();
        assertTrue(reserve.contains("104857600 - ?"));
        String list =
                configuration
                        .getMappedStatement(namespace + "selectList")
                        .getBoundSql(parameters)
                        .getSql();
        assertTrue(list.contains("'READY'"));
        assertTrue(list.contains("ORDER BY"));
        assertTrue(list.replaceAll("\\s+", " ").contains("\"CREATED_AT\" DESC , \"FILE_ID\" DESC"));
        assertThrows(ApiException.class, () -> new ListQuery(null, -1, 20, ListQuery.Sort.idAsc));
        assertThrows(ApiException.class, () -> new ListQuery(null, 0, 101, ListQuery.Sort.idAsc));
        assertThrows(
                IllegalArgumentException.class, () -> ListQuery.Sort.valueOf("name; DROP TABLE"));
    }

    /** 제품 XML과 record 자동 이름 매핑 정책을 실제 parser에 전달한다. */
    private Configuration configuration() throws Exception {
        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setArgNameBasedConstructorAutoMapping(true);

        for (var resource :
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:mappers/**/*.xml")) {
            try (var input = resource.getInputStream()) {
                new XMLMapperBuilder(
                                input,
                                configuration,
                                resource.toString(),
                                configuration.getSqlFragments())
                        .parse();
            }
        }

        return configuration;
    }
}
