package com.example.starter.auth.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.TestPolicies;
import com.example.starter.TestTransactions;
import com.example.starter.auth.model.AuthError;
import com.example.starter.auth.model.AuthLogin;
import com.example.starter.common.web.ApiException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** 실제 JDBC store의 guard/commit 순서를 관찰하며 DB 원자성 실증과 구분한다. */
class JdbcStateContractTest {
    /** 과거 hash 재사용은 해당 sid만 폐기하고 commit한 다음 401을 전달한다. */
    @Test
    void reuseRevocationIsCommittedBeforeError() {
        var calls = new ArrayList<String>();
        var transaction = new TestTransactions();
        var login = login();
        AuthStateMapper mapper = mapper(calls, login, "");
        var store = new JdbcAuthStateStore(mapper, transaction, TestPolicies.local());
        var failure =
                assertThrows(
                        ApiException.class, () -> store.rotate("used-hash", "new-hash", 2_000));
        assertEquals(AuthError.TOKEN_REFRESH_REUSED, failure.error());
        assertEquals(
                List.of(
                        "selectSidByHash",
                        "selectLogin",
                        "insertGuard",
                        "selectGuardForUpdate",
                        "selectLogin",
                        "updateRevoked"),
                calls);
        assertEquals(1, transaction.commits);
        assertEquals(0, transaction.rollbacks);
    }

    /** 저장소 fence는 정상 서명 JWT가 있어도 인증 상태 조회를 거부한다. */
    @Test
    void fencePreventsStateUse() {
        var transactions = new TestTransactions();
        var store =
                new JdbcAuthStateStore(
                        mapper(new ArrayList<>(), login(), UUID.randomUUID().toString()),
                        transactions,
                        TestPolicies.local());
        assertEquals(
                AuthError.AUTH_STATE_UNAVAILABLE,
                assertThrows(ApiException.class, () -> store.require(login().sid(), 2_000))
                        .error());
        assertEquals(1, transactions.rollbacks);
    }

    /** idle/absolute 경계에서 같음은 만료이며 revoke 상태는 다시 활성화되지 않는다. */
    @Test
    void lifetimeBoundariesDoNotResurrect() {
        AuthLogin login = login();
        assertTrue(login.isActive(2_000));
        assertEquals(false, login.isActive(5_000));
        assertEquals(false, login.isActive(10_000));
    }

    /** Redis Lua cjson의 double 한계를 넘는 내부 userId도 문자열로 보존한다. */
    @Test
    void stateJsonPreservesBigintUserId() {
        var json =
                JsonMapper.builder()
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build();
        var login =
                new AuthLogin(
                        UUID.randomUUID().toString(),
                        Long.MAX_VALUE,
                        1,
                        1,
                        1,
                        5_000,
                        10_000,
                        null,
                        null,
                        "[]",
                        "synthetic-hash");
        String encoded = json.writeValueAsString(login);
        assertTrue(json.readTree(encoded).get("userId").isString());
        assertEquals(Long.MAX_VALUE, json.readValue(encoded, AuthLogin.class).userId());
    }

    /** 실제 mapper 호출을 기록하는 시험 대역이며 DB 연결을 열지 않는다. */
    private AuthStateMapper mapper(List<String> calls, AuthLogin login, String fence) {
        return (AuthStateMapper)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {AuthStateMapper.class},
                        (instance, method, args) -> {
                            calls.add(method.getName());

                            return switch (method.getName()) {
                                case "selectSidByHash" -> login.sid();
                                case "selectLogin" -> login;
                                case "selectGuardForUpdate" -> fence;
                                default -> 1;
                            };
                        });
    }

    /** hash 원문 token 없이 단기 상태만 구성한다. */
    private AuthLogin login() {
        return new AuthLogin(
                "b5cf43c1-9585-43cd-b637-e458bb487956",
                1,
                1_000,
                1_000,
                1_000,
                5_000,
                10_000,
                null,
                null,
                "[]",
                "current-hash");
    }
}
