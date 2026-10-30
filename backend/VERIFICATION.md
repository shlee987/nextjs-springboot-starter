# 백엔드 검증 기록 (2026-10-30)

이 기록은 백엔드 공통 기반의 독립 검증과 승인 후 실제 연동 결과를 구분한다.
아래 앞부분은 실연동 전에 확인한 기록입니다. 실제 연동 결과는 FOUNDATION-EXEC-001 절에서, 콘솔 개선과 파일 기능은 각 후속 절에서 확인하세요.
기존 컨테이너/volume·starter_local·Redis0는 보존했고 커밋/push는 수행하지 않았다.
기존 2026-10-26 연결 결과는 이번 인증/감사 기능의 통과 증거로 사용하지 않는다.

로컬 로그 개선 당시 기본 시험은67개였습니다. 최신 파일 기능과 실제 실행·후속 수정 결과는 마지막 SECFILE-EXEC-001 절을 참고하세요. 이전 실제 연동 결과를 새 파일 기능의 실행 증거로 사용하지 않습니다.

## 구현한 기능

- 기존 config/web를 common으로 이동하고 오류 경계·ApiResponses·JSON 제한을 재사용했다.
- AppProperties, UTC 날짜3형식·밀리초 절삭·정밀 decimal 문자열·큰 ID·페이지/정렬 검증을 추가했다.
- user/department/permission의 조회·변경 Service와 quoted MyBatis SQL, 명시적 rollbackFor 경계를 추가했다.
- Redis Lua와 JDBC guard 잠금의 AuthStateStore, 단회 refresh·tombstone·3종 동시 로그인 정책·한도·fence를 구현했다.
- Spring/Nimbus JWT·Argon2·쿠키/Origin/CSRF·API stateless chain·조건부 prelogin Session을 연결했다.
- core/auth-jdbc/session-jdbc/local SQL5개, 독립 history runner와 local DB 개발 키 초기화 코드를 작성했다.
- local 콘솔/shared JSON 두 파일·회전 정책·SQL 진단·요청 종료 로그, append-only 감사와 실패 정책을 구현했다.
- 공개14개 operation과 실제 OpenAPI export, 기본 격리 시험 및 별도 liveAuthTest를 작성했다.
- README/.env.example/CHANGELOG/본 기록을 갱신했다. frontend/infra/루트/docs 소유 파일은 수정하지 않았다.

## 빌드 확인 방법

Temurin25 HotSpot·Java25 ADOPTIUM toolchain, Gradle9.8.2로 실행한다. 경로는 backend 기준이다.

```sh
./gradlew compileJava --write-locks
./gradlew test bootJar --write-locks
./gradlew test bootJar --offline
```

초기 의존성 해석·기존 시험 적응 과정의 실패는 수정했고 최종 결과는 아래 기록으로 구분한다.
실행 전 R1의 `test bootJar --offline`는 2026-10-29 통과했다. 20 suites/54 tests, 실패0·오류0·skip0이며 live 태그 시험은 기본 task에서 제외했다.
OpenAPI는 13 paths/14 operations/28 schemas, SHA-256 `8ea277c75f3ebaf7ff1b0cada89c22c59557d400c64a4a52725926511cedf5f4`다.

상세 JUnit 결과는 `build/test-results/test/TEST-*.xml`, HTML은 `build/reports/tests/test/index.html`,
JAR은 `build/libs/starter-api-0.1.0-SNAPSHOT.jar`다. private 작업 로그는 저장소 밖에 보관했다.

실제 springdoc 출력은 `build/openapi/openapi.json`이다. `OpenApiExportTest`가 제품 Controller의
메서드·DTO·annotation에서 생성한 HTTP 응답을 검증한 후 쓴다. MOCK 컨텍스트에 DataSource·
RedisConnectionFactory·SigningKeys가 없음을 assert한다. HTTP 포트 바인딩·DB/Redis 접속·
migration·서명 키 생성이 없었다. 13 paths/14 operations/28 schemas, 내부 $ref 해소와 null/필수 필드,
UTC custom string·cookie/CSRF 모델·204를 검사한다. 설계 담당이 docs/** 복사를 소유한다.

Java는 [google-java-format1.37.0](https://github.com/google/google-java-format/releases/tag/v1.37.0)의
AOSP4칸·100열 모드를 일회 외부 도구로 사용했다. 제품 의존성에 formatter를 추가하지 않았다.
Java import 정리와 `git diff --check -- backend`를 확인한다.

## ID별 검증 범위

`부분`은 아래 독립 시험만 통과했다는 뜻이다. 외부 저장소/HTTP 전체 통과와 구분한다.

| ID | 이번 독립 시험/코드 | 미실행 범위 |
| --- | --- | --- |
| BF01 부분 | FoundationConfigurationTest, FoundationValuesTest, DisabledRepositoryTest; 4환경·필수키·typed enum/수명·off bean | 실제4환경 전체 앱 준비 |
| BF02 부분 | FoundationValuesTest; STRICT3형식/윤년/offset/UTC·절삭/JSON 왕복 | PG timestamptz 왕복 |
| BF03 부분 | FoundationValuesTest; bigint/decimal/count/null/[]/code point/메시지 fallback | 실제 데이터·프론트 정밀 계산 |
| BF04 부분 | MapperContractTest; 모든 XML 파싱·동적 정렬·LIKE 바인딩/count 조건·정리 SQL | 실제 PG 대문자/camel 매핑·쿼리 계획 |
| BF05 미실행 | 명시 transaction·동일 DataSource/MyBatis | DB01~DB07 rollback/TEMP/pool 재사용 |
| LG01 부분 | SafeLogContractTest, LogDirectoryGuardTest; JSON/UTC/비밀 미노출·local 파일 없음·임시 shared probe | 두 파일 INFO/WARN/ERROR·100MB/일자 rollover·gzip/보관/cap 실증 |
| LG02 부분 | ApiContractTest, SecurityBoundaryTest; 서버 trace/MDC 정리·HEAD/오류 경계 | 병렬/async/error 단회·프록시/IPv6 실증 |
| LG03 부분 | SQL/slow interceptor 분리, MapperContractTest·SafeLogContractTest | 실제 동적 SQL/slow시간·bind/cause 표식 수집 |
| AU01 부분 | AuditFailureContractTest; 실제 advice/writer STARTED/종료·실패503·BEST_EFFORT 결과 보존 | DB 이력 왕복·실제 권한 거부 |
| AU02 부분 | AuditFailureContractTest, TestTransactions; timeout3s/commit 실패·변경 동기화 코드 | 실제 checked/runtime rollback·pool 대기/DB commit 불명확 |
| AU03 부분 | MapperContractTest; 감사 Mapper INSERT/SELECT만·기간/권한 Service·공개 allowlist | DB 권한/자기 조회 실제1회·보존 운영 |
| AT01 부분 | PasswordContractTest·LoginFailureContractTest·JwtClaimContractTest·FoundationValuesTest; Argon2 비용/seed·claim/시각·권한 snapshot | 실제 RSA 서명·kid/키 회전·정상/미존재/비활성 로그인 |
| AT02 부분 | SecurityBoundaryTest·JwtClaimContractTest; 세션만 API401·sid/sub/auth_time·scope 무시/손상503 | 저장소 sid/권한/데이터 범위 왕복 |
| AT03 부분 | JdbcStateContractTest; 재사용 폐기 commit 후 오류 전달 순서 | Redis/JDBC 실제 CAS/병렬 회전/응답 유실 |
| AT04 부분 | JdbcStateContractTest; 만료 경계·SQL/Lua MAX/cap·HEAD/refresh touch 제외 코드 | 실제 Clock/병렬 touch/유휴·절대 만료 |
| AT05 미실행 | Lua 사용자 단일 slot·JDBC guard·allow/deny-new/replace-old | 두 서버 병렬 동시 로그인3정책 |
| AT06 미실행 | liveAuthTest의 A/B cookie·logout 시나리오 작성 | 실제 두 서버 실행 |
| AT07 부분 | JdbcStateContractTest fence 거부·변경 workflow/같은 commit 감사 작성 | 중간 장애/역할 경쟁/DB·Redis fence 복구 실증 |
| AT08 부분 | DisabledRepositoryTest; JDBC+prelogin off Redis/Session bean 없음 | 양 모드 시작·장애503/fallback 부재 실제 확인 |
| AT09 부분 | AuthCookiesTest·SecurityBoundaryTest·FoundationConfigurationTest; 실제 chain의 XOR/Origin/쿠키/세션없음·만료access skip | 브라우저 다중 탭·실제HTTPS/프록시·login/logout CSRF 교체 |
| AT10 부분 | 원자 bucket SQL/Lua와 Retry-After 경계·별도 live 시나리오 작성 | 실제 5회/15m·IP30·병렬·TTL·success IP 보존 |
| AT11 부분 | off bean 검증·JSON 허용 타입 왕복·JDBC bytea converter/quoted CRUD 작성 | on A/B Session 저장/복원·TTL |
| AT12 미실행 | 독립 schema/history·off 조건·runner 순서 작성 | 선택 DDL 생성/제거/재기동 유지 |
| AT13 부분 | JwtClaimContractTest 세대/claim·LoginReferencesTest HMAC·키/세대 readiness 코드 | 개발 키 생성/재시작/A·B·회전/복구 snapshot 부활 방지 실증 |
| FEI01/FEI02 담당 외 | 실제 spec 인계만 | 프론트 Orval/브라우저/정밀 계산·캐시 시험 |
| SEC01 부분 | ApiContractTest·SecurityBoundaryTest; 필터/MVC/error/HTML/검증·원문 표식·HEAD 회귀 | 실제 container parser/TLS/프록시·외부 저장소 장애 응답 |

## 실연동 전에 작성한 실행 계획

다음은 계획이며 아직 실행하지 않았다. 실제 대상의 기존 상태/백업이 확인되기 전에는 실행하지 않는다.

| 대상 | 계획 명령/작업 | 쓰기 영향 |
| --- | --- | --- |
| PostgreSQL127.0.0.1:25432/starter_local (확인 필요) | local 환경의 `./gradlew migrateDatabase` | public core11테이블·permission10행, local 개발키 테이블·합성 사용자2/역할2/부서1·연결행·독립 history |
| 같은 PG, redis(default) | A/B `bootRunLocal` 또는 빌드 JAR을 port8080/8081·서로 다른 serverId로 기동 | 최초 RSA3072 DB 저장(기존 키면 조회), 상태 정책 digest·감사 행 |
| Redis127.0.0.1:26379 DB0 (확인 필요) | 위 A/B 앱과 liveAuthTest | starter:local:auth:v1의 로그인/locator/history/rate/fence·정책; prelogin off에는 세션 listener/probe 없음 |
| JDBC 선택 | APP_AUTH_STATE_STORE=jdbc로 승인된 migration/기동 | public AUTH_*4테이블·독립 auth history·상태/rate/정리 쓰기, Redis 접근 없음 |
| JDBC+prelogin | APP_AUTH_PRELOGIN_SESSION_ENABLED=true 후 승인된 migration/기동 | auth_session 두 테이블/history·JSON probe save/read/delete·60초 정리 |
| Redis+prelogin | 같은 옵션으로 승인된 기동 | starter:local:prelogin 세션/probe·indexed listener |
| 실제 HTTP | 아래 liveAuthTest | 합성 localuser 로그인/갱신/폐기와 미존재 계정 rate·감사 행. 현재 harness는 계정/권한 값을 수정하지 않음 |

공유된 기존 local DB 대신 별도 승인한 격리 DB/Redis DB번호를 사용하는 것이 우선이다. 그 이름/권한은
아직 정하지 않았다. DB 생성·삭제나 Redis 전체 flush는 이 계획에 포함하지 않는다.
선택모드·prelogin·동시 정책은 각각 같은 설정의 A/B로 실행해야 하며, 정책 전환은 기존 자격 전체
폐기/새 세대와 JDBC 정책 기록 전환을 먼저 승인한다.

```sh
# A/B 서버가 이미 승인된 대상/같은 키·정책으로 준비된 뒤 실행하는 명령이다.
# 환경변수 STARTER_LIVE_AUTH_TESTS=approved,
# STARTER_TEST_SERVER_A=http://127.0.0.1:8080,
# STARTER_TEST_SERVER_B=http://127.0.0.1:8081을 외부 주입한다.
./gradlew liveAuthTest
```

현재 live harness는 A/B login→me/HEAD→logout, 회전/재사용, 병렬 refresh 단회, 미존재 계정 제한을
검증하도록 작성했다. 만료시간·3정책·서비스 변경/fence fault·Session on/off·DB TEMP·pool의 추가 실제
시험은 아직 실행 증거가 없다. 제품 장애용 endpoint를 추가하지 않았다.

실행 전 PG 전체/선택 schema와 선택 Redis 상태의 승인된 백업·복원 경로를 정한다. 승인 후 시작한
A/B 프로세스만 PID로 종료하며 기존 프로세스/컨테이너/volume은 유지한다. 신규 스키마/seed/키·감사
쓰기는 단순 HTTP logout으로 되돌아가지 않으므로 정확한 복구는 사전 백업 복원 또는 별도 승인한
격리 대상 제거로 한정한다. 오래된 인증 snapshot은 재사용하지 않고 전체 재로그인을 강제한다.
Flyway clean, baseline 덮기, compose down -v, Redis FLUSHALL/FLUSHDB를 자동 실행하지 않는다.

실행 보류 근거는 [DB 실행 규칙](../docs/common-rules/docs/conventions/common/database-access.md)의
“데이터 변경·DDL·변경 가능한 프로시저·마이그레이션 실행은 대상 DB·환경, 실행 SQL 또는 명령,
영향 범위와 복구 방법을 제시하고 사용자의 명시적 승인 후 수행한다”이다. 간접 앱/시험 쓰기에도 적용된다.

## 구현 세부 구체화·배포 입력

- Boot4.1.2 BOM이 BC 버전을 관리하지 않아 공식 안정판 bcprov-jdk18on1.86을 명시했다.
- 감사 전용 외부 HMAC secret의 키 이름을 APP_AUDIT_LOGIN_REFERENCE_KEY_LOCATION으로 구체화했다.
  local은 개발 서명 키에 용도 label을 붙여 파생하며 공개/기본 운영 키를 내장하지 않는다.
- snapshot 복구 세대는 APP_AUTH_NAMESPACE_GENERATION(default v1), Redis 물리 prefix의 suffix,
  JWT asg claim, 세대로 용도 분리한 refresh SHA-256으로 구체화했다. base namespace는 계약대로 유지한다.
  JDBC는 세대 전환 시 이전 상태/정책의 승인된 정리와 guard/fence 복구가 별도로 필요하다.
- 공유 파일 로그는 외부 library raw message 대신 framework_event와 정제 타입/코드 위치만 기록한다.
  local Swagger 자원에만 self CSP를 적용하며 API 오류 CSP와 Origin/CSRF를 완화하지 않는다.
- 보관 기간·실제 domain/proxy/TLS·운영 key/secret 공급·권한 grant·백업/복구 책임은 배포 시 입력이다.
  외부 SSO/MFA/가입/복구/관리 화면·운영 배포·ASVS 전체 검증은 이번 구현/시험 완료 범위가 아니다.

추가 정적 검증: migration5개의 명시적 constraint/identity sequence149개가 이름 중복 없이 대문자·63byte 이내다. 실제 PostgreSQL 실행 검증은 남아 있다.
AuditTransactionOrderTest는 필수 조회의 STARTED→업무→종료 감사 순서와 모의 자원 동시 점유1개를 확인하며 실제 DB pool 검증을 대신하지 않는다.


## BACK-FOUNDATION-R1 수정 검증 (2026-10-29)

| 지적 | 수정과 DB 없는 증거 | 남은 실제 검증 |
| --- | --- | --- |
| BFR1-001 P1 | FoundationMigrations/IndependentHistoryInitializer: 독립 history 유지, 자동 baseline 금지, 최초 신규 history만 검증 후 명시적 baseline0→migrate. 기존 core/auth/local history validate와 완료 상태, public relation 이름/종류의 SQL manifest 정확 일치·unknown routine/type 부재, local 소유 schema 충돌을 확인하며 실패 시 초기화하지 않는다. runner 전체 transaction advisory lock. IndependentHistoryContractTest2개는 설치된 Flyway12.4.0의 실제 ResolvedMigration 상태 판정(V1/localV2 PENDING·baseline1의 V1 무시 반례), 제품 설정/4경로/5파일, 합성 catalog의 unknown/소유 객체 충돌/누락/type 거부를 확인. | 실제 PostgreSQL의 catalog/DDL/이력 생성·동시 runner·최초/재실행은 아래 계획대로 미실행 |
| BFR1-002 P2 / AUTH03 | AuthLoginService.login의 보상 경계에서 token 발급 후 callback으로 permissions JSON parsing과 사용자 projection까지 완료. AuthController는 성공 결과만 쿠키로 발급. LoginProjectionContractTest2개: 상태 생성 후 실제 UserService projection 실패→Set-Cookie 없음/새 sid 폐기→후속 deny-new 로그인200, 손상 JSON503·폐기, 보상 실패 시 원래 오류/suppressed 유지·남은 상태에 다음 deny-new409. 기존 ApiContractTest는 공개 오류/중첩 원인 은닉 유지. | Redis/JDBC 실제 부분 실패/폐기·HTTP 응답 장애 실증은 미실행 |
| BFR1-003 P2 / LOG02·AUD01 | AuditEvent/두 INSERT의 CLIENT_IP inet parameter에 최외곽 RequestContext 직접 peer 연결. Dispatcher 이전 필터 구간도 thread 문맥을 사용하고 finally 제거. AuditPeerContractTest2개: IPv4/IPv6 정규화·위조 Forwarded/X-Forwarded-For 무시·양 감사 parameter 바인딩·요청 후 SYSTEM NULL, 공개 DTO/SELECT·진단 MDC/이벤트 toString 원문 IP 없음. | 실제 inet 왕복/비동기 요청·pool thread 재사용 실증은 미실행 |

실행: `./gradlew test --tests '*IndependentHistoryContractTest' --tests '*LoginProjectionContractTest' --tests '*AuditPeerContractTest' --offline` 6개 통과 후 `./gradlew test bootJar --offline` 전체54개/20 suites 통과(실패0·오류0·skip0, live 제외). JAR 생성과 OpenAPIExportTest도 포함한다.
초기 회귀검사의 baseline1 반례에서 합성 Flyway context의 lastApplied 설정 누락을 발견해 실제 baseline 적용 상태로 수정한 후 재검사했다. 제품 V1/V2 검증 조건을 낮추지 않았다.
설치된 flyway-core12.4.0 sources의 Flyway.migrate는 non-empty schema/history 없음/자동 baseline=false에서 예외를 던지고, history 존재 후 DbMigrate로 진행함을 확인했다. DbBaseline.baseline은 새 history에 설정 버전 marker를 생성한다. 설치된 PostgreSQLDatabase.getRawCreateScript의 history/index 이름도 catalog allowlist와 대조했다. 라이브러리 소스 검사와 상태 판정 시험이며 실제 migrate/baseline의 DB 왕복 실행 증거는 아니다.
OpenAPI는 기존 공개 SHA-256 `8ea277c75f3ebaf7ff1b0cada89c22c59557d400c64a4a52725926511cedf5f4`와 동일하다. 내부 보상 경계/감사 영속/migration 초기화만 변경했으며 공개 path·DTO 계약 변경이 없다.

### BFR1-001 승인 후 최초 설치·재실행 계획 — 미실행

아래3종은 각각 **신규 전용 PostgreSQL DB**를 승인받아 실행한다. 기존 starter_local을 지우거나 재사용하여 최초 설치로 가장하지 않는다. 대상 host/port/DB명·migration 계정·백업 위치를 실행 직전 확정한다. 기존 후보는127.0.0.1:25432이며 별도 승인 전 접속/생성도 하지 않는다. migration 전용 CLI만 사용하고 제품 앱/키 생성/Redis 연결은 필요 없다. localV2 seed 쓰기는 승인 범위에 포함해야 한다.

| 신규 DB 시나리오 | 동일 DB에서 첫 실행·두 번째 동일 실행 | 기대 SQL migration 성공 행(버전별 정확히1개) |
| --- | --- | --- |
| redis-local | local / APP_AUTH_STATE_STORE=redis / prelogin=false / migrateDatabase, 이후 동일 명령 재실행 | public.flyway_schema_history V1=1; public.local_seed_schema_history V1=1,V2=1; AUTH_*·auth history·auth_session 없음 |
| jdbc-local | local / APP_AUTH_STATE_STORE=jdbc / prelogin=false / migrateDatabase, 이후 동일 명령 재실행 | 위 core/local 각각1 + public.auth_jdbc_schema_history V1=1; AUTH_*4테이블, auth_session 없음 |
| jdbc+prelogin-local | local / APP_AUTH_STATE_STORE=jdbc / prelogin=true / migrateDatabase, 이후 동일 명령 재실행 | 위 jdbc/core/local 각각1 + auth_session.flyway_schema_history V1=1; SPRING_SESSION/ATTRIBUTES2테이블 |

각 첫 실행의 SQL 적용 수는 순서대로3/4/5이고 두 번째는0이다. 신규 auth/local history의 BASELINE0 marker는 SQL 실행 횟수에 포함하지 않는다(각1개 유지). core는 기존 history를 유지하며 새 baseline을 넣지 않는다. auth_session은 Flyway가 새 schema를 만든 SCHEMA marker를 가질 수 있고 V1은 반드시 SUCCESS1개다. 각 history의 installed_rank/version/type/checksum/success를 실행 전후 비교하고, 재실행 후 기존 행/checksum·seed 사용자2/역할2/부서1·permission10개·연결행·identity 순서가 변하지 않는지 확인한다. 개발키 테이블은 생기되 제품 앱 미기동이므로 key 행/실제 서명 키 생성은0이어야 한다.

별도 승인된 충돌 fixture에서 core 완료/후속 history 없음 + AUTH_LOGIN 선점, 또는 starter_local_only schema 선점, unknown public table/type/routine을 각각 넣어 초기화를 거부하고 새 후속 history·소유 객체 변경이 없음을 확인한다. 이미 존재하는 core/history/seed는 수정·삭제하지 않는다. 두 runner 동시 시작도 전역 advisory transaction lock 아래 순서대로 완료되고 SQL별1회인지 확인한다.
실패 시 진입 중단·안전한 오류 분류와 각 history의 실제 commit 상태를 확인한다. runner 간 원자 일괄 rollback은 제공하지 않으므로 완료된 core/auth는 보존하고 동일 migrate로 재개한다. 새 baseline0 후 SQL이 실패해도 history 삭제/repair/버전 덮기를 자동 수행하지 않는다. 시험 DB 폐기는 전용 대상 재확인과 별도 승인을 거치며 기존 DB·volume·Redis에는 clean/flush/reset하지 않는다. 이 계획은 실행 승인이 아니다.


## FOUNDATION-EXEC-001 실제 연동 완료 범위 (2026-10-29)

사용자는 지정 로컬 격리 실행계획과 신규 테스트 환경 사용을 명시 승인했다. 후속 조정으로 프론트 origin은 localhost:13000, Redis prelogin on은 추가 빈 DB12를 사용했다. 초기 미실행/승인 대기 문구는 위 과거 단계의 이력이며 이번 절과 혼동하지 않는다.

### 대상·실행·보존

- 기존 starter-project PostgreSQL18.6(127.0.0.1:25432)/Redis8.10.2(127.0.0.1:26379)를 유지했다. DB 목록에서 검증 이름 부재, Redis13/14/15 각각 DBSIZE0 확인 후 시작했다. Redis12도 추가 사용 직전 DBSIZE0을 확인했다.
- 신규 PG는 `starter_verify_20261029_redis`, `starter_verify_20261029_jdbc`, `starter_verify_20261029_session`만 생성했다. 기존 `starter_local`은 변경하지 않았고 public 테이블1개, Redis0 DBSIZE0을 최종 조회했다.
- 각 PG에 `migrateDatabase --offline`를 동일 설정으로2회 실행했다. 최초 SQL 적용3/4/5개, 재실행 모두0. coreV1·localV1/V2·선택authV1·선택sessionV1 성공 행은 각 정확히1개다. auth/local 신규 baseline0과 session SCHEMA marker는 SQL 횟수에서 제외했다. seed 사용자2/권한10/역할2/부서1, 기동 전 키0행을 확인했다.
- 제품 A/B 기동으로 각 검증 DB에 개발 RSA3072 키1행이 생성됐다. 반복 기동/두 노드에서도 각1행 유지, 실제 JWT 교차 서버 검증으로 키 공유·복원을 확인했다. 원문 개인키/쿠키/JWT/refresh는 공개 기록과 메시지에 출력하지 않았다.
- Redis allow/deny-new/replace-old는 DB15/14/13으로 분리했고 물리 generation은 verify-20261029다. full Redis+prelogin on은 DB12 allow로 독립 구성했다. Redis0 및 기존 정책 anchor는 삭제/덮어쓰지 않았다.
- 최종 기본 Redis/allow off A/B만 유지한다: http://127.0.0.1:18080 (`verify-a`, PID6832), http://127.0.0.1:18081 (`verify-b`, PID11048). origin http://localhost:13000, PG redis 검증DB/Redis15. 추가 JDBC·Session·Redis12 on 서버 및 본인이 만든 helper 프로세스만 종료했다. 기존 프로세스·컨테이너·volume은 종료하지 않았다.
- 실행 JAR는 private 불변 SHA 사본에서 로드한다. 작업 빌드 JAR를 실행 도중 덮어쓴 후 Lua 리소스 재읽기503을 관찰해 원인을 확인했고 불변 사본으로 기본 A/B를 복구·실제 재검증했다. 저장소 장애/자료 초기화로 회피하지 않았다. 이후 빌드는 기동 중 사본을 변경하지 않는다.

### 실제 통과와 근거

| 범위 | 실제 결과 |
| --- | --- |
| BFR1-001 / AT12 | 신규3DB 최초+재실행, migration별 정확1회·독립 history·V1/V2 실행·기동 전 키0 확인. 앱 재기동도 추가 migration0. unknown fixture/동시 migration 경쟁은 이번 실제 실행에서 별도 주입하지 않았다. |
| AT01/02/03/06/09/10 | 기존 liveAuthTest4개를 Redis15/JDBC/Session 각각 실행해12개 통과. Redis12 full prelogin on에서4개 추가 통과(총16 실제 HTTP 시험). A로그인→B me/HEAD, A logout→B401, 단회 회전·과거 hash 재사용 sid폐기, 병렬 회전·미존재5회 실패 후429/Retry-After. 실제 cookie 자격은 메모리 client만 보유했다. |
| AT05 | Redis15 allow 병렬로그인200/200·양 sid200, Redis14 deny-new409/200·401/200, Redis13 replace-old200/200·401/200. refresh200은 새 로그인으로 계산되지 않고 logout204. JDBC 실제 실행은 allow이며 JDBC 제한2정책 경쟁은 별도 미실행이다. |
| BF03/04 / AU03 | Redis/JDBC/Session3모드 HTTP: USER self200·관리목록403, ADMIN 사용자목록/상세/부서/접근·보안감사200. 감사 공개 응답에 clientIp/loginReference 없음. SQL 바인딩 검색/count/안정 정렬을 실제 PG에서 확인. |
| BF02/03/05 / DB01~05/07 | 실제 제품 DataSource/SqlSessionTemplate의 두 Mapper가 같은 pg_backend_pid. TEMP ON COMMIT DROP의 생성→입력→다른Mapper조회→commit 후 제거, RuntimeException/checked exception rollback과 같은 pool PID 재사용 후 이름/데이터 잔류 없음. bigint9223372036854775807·numeric30,10 정확값·timestamptz(3)의 +09→UTC .123 왕복, pool UTC. DB06 범용 optimistic 갱신 경쟁 전체는 미실행. |
| AU01/02 | pool 최대1에서 실제 REQUIRED 조회 STARTED+SUCCESS 두행과 업무 완료. ACCESS_AUDIT에 다른 실제 연결로 ACCESS EXCLUSIVE lock을 걸어 실제 timeout: REQUIRED503·BEST_EFFORT 업무 결과 보존, 필수 권한변경 rollback/기존 permission복원·JDBC BLOCKED fence0 확인. 잠금은 rollback으로 해제했고 테이블/이력을 삭제하지 않았다. commit 불명확/DB 단절 전체는 미실행. |
| AT07 | JDBC 실제 User 역할 권한에서 DEPARTMENT_READ 제거→기존 sid폐기·새 로그인 권한403, 원래2권한 복원. 같은 transaction의 감사·fence 해제를 확인. 별도 합성 Redis user915002에 실제 영속 fence(PTTL=-1), 타 operation 해제503/차단 유지, 전체 폐기→소유자 해제→기존 sid401/TTL 복원. 실제 Redis-DB commit 중간단절 전체 workflow는 미실행. |
| AT04 / LG02 | 실제 A/B와 Redis15 저장소 왕복: HEAD·refresh는 lastActivityAt 유지, GET me는 증가. 별도 합성 user915003의 짧은 수명 fixture로 Redis 실제 TTL 만료·단조 touch·absolute cap·부활 거부. 30분/8시간 전체 벽시계 대기 시험은 별도 미실행. |
| BFR1-003 / AUD01 | HTTP 서버 이력은 양 감사 CLIENT_IP127.0.0.1, HTTP 없는 제품 서비스 helper의 SYSTEM 이력은NULL 집계. 실제 Forwarded/X-Forwarded-For 위조 요청의 STARTED/SUCCESS2행 모두 직접peer127.0.0.1 확인. IPv6 직접 socket 요청은 bind127.0.0.1 환경에서 미실행이며 정규화 단위검사와 구분. |
| AT08/11 | 실제 전체 StarterApplication 문맥: JDBC off는 Redis factory/Session repository0, JDBC on은 repository1/Redis0, Redis off는 선택 DB15·Session0. Spring Session 전용 filter/listener 없음(설치 Boot의 일반 Redis annotation listener container는 세션 listener가 아니다). |
| AT11 Redis/JDBC on | JDBC on 전체 앱 A/B 정상, 실제 독립 repository A저장/BJSON복원·TTL 거부·delete·quoted cleanup SQL 통과. Redis12는 full 인증 앱 on A/B 기동/readiness와 live4개 통과. 전체 제품 설정을 로드한 독립 A/B 문맥에서 실제 Spring Session listener running·A저장/BJSON복원·TTL/delete·양쪽 SessionExpiredEvent 수신 확인. off 문맥 복귀 후 repository/filter/전용 listener0. 초기 Redis13 수동 repository/옵션 전용 문맥 결과는 보조 증거이며 full12 결과로 보완했다. |
| AT13 | 개발 키 각1행 유지·실제3072/RS256 JWT와 A/B 공유·기동 복원. 실제 외부 키파일 공급/키회전/구키차단/과거 snapshot 복원 전체는 미실행. |

### 실기동에서 발견한 수정 — 독립 재리뷰 대상

| ID | 원인·수정 파일 | 회귀와 실제 재검증 |
| --- | --- | --- |
| LVR1-001 | FoundationMigrations 설정 클래스 기본 이름과 @Bean foundationMigrations가 충돌해 제품 boot 실패. 설정 클래스 이름만 foundationMigrationConfiguration으로 명시하고 기존 초기화 bean/DependsOn은 유지. | IndependentHistoryContractTest의 실제 Spring bean 중복금지 등록 검사를 추가. 전체 제품 Redis/JDBC/Session A/B 정상 기동·migration history 보존 확인. |
| LVR1-002 | 표준 CSRF 인증전략이 매 요청 JWT 인증에도 쿠키를 지워 보호 GET 뒤 logout403. SecurityConfiguration의 stateless chain에 NullAuthenticatedSessionStrategy를 CSRF 인증전략으로 지정. CSRF/Origin 검증은 유지하고 login/logout Controller의 명시 교체를 유지. | SecurityBoundaryTest에 실제 JWT converter/filter 성공 GET→같은 XOR header POST200·쿠키 삭제 없음·Session 없음 추가. 실제 A/B login200→GET200→logout204,3mode/Redis on live 통과. |
| LVR1-003 | application.yml의 존재하지 않는 SessionJdbcAutoConfiguration 제외명이 실제 JdbcSessionAutoConfiguration을 막지 못해 off에도 기본 repository/cleanup 생성. 설치된 Boot 실제 클래스명으로 수정. | DisabledRepositoryTest에 설치된 제외 클래스/실제 JDBC 제외 검사를 추가. 수정 후 제품 전체 문맥 off repository0, on만 실제 quoted SQL repo. 잘못된 public SPRING_SESSION cleanup 오류 소멸 확인. |
| LIVE-TEST-001 | 병렬refresh의 CAS 승자도 재사용 폐기를 발급 전 require에서 관찰하면401이므로 기존 무조건200/401 단정은 안전한 거부 경쟁을 실패로 오인. | LiveAuthContractTest는 HTTP200 최대1·정확히1개 TOKEN_REFRESH_REUSED·나머지401은 TOKEN_REVOKED만 허용·최종 sid401을 검사.200/200/임의오류는 허용하지 않는다. 제품의 추가 상태확인을 제거하지 않았고4mode live16개 통과. |

최종 독립 `test bootJar --offline`: 20 suites/57 tests, 실패0·오류0·skip0(live 제외). 별도 live16개 통과다. formatter와 diff 검사를 완료했다. OpenAPI는13 paths/14 operations/28 schemas와 기존 SHA-256 `8ea277c75f3ebaf7ff1b0cada89c22c59557d400c64a4a52725926511cedf5f4`를 유지한다. 공개 path/DTO 변경은 없다.

private evidence는 모드별 exec-migrate/live XML·제품 safe log·HTTP 요약·ContextProbe/LiveDataProbe/RedisFullOptionProbe의 실행 로그로 보존했다. 임시 helper의 컴파일/API 착오(비공개 JdbcSession 구현타입·Lettuce lifecycle·guard 컬럼)와 listener 수 가정은 private harness에서 수정했으며 제품 결함3건과 구분했다. 새 테스트 플랫폼/제품 probe endpoint를 추가하지 않았다.

### 잔류 상태·한계

신규 PG3DB와 Redis12~15의 정책·로그인 tombstone·한도·감사·개발키/시험 자료는 보존한다. USER permission은2개로 복원됐으나 변경 이력/연결행 작성시각은 남는다. helper가 발급한 일부 sid와 Redis915002 폐기 상태는 원래 수명/정리 정책까지 남을 수 있다. 테스트 계정 비밀번호는 backend helper에서 변경하지 않았다(프론트 승인 시험의 변경/복원은 프론트 담당 기록을 따른다). Session probe는 삭제/만료됐고 minute expiry metadata는 자체 TTL까지 남을 수 있다. DB/Redis flush·history repair/삭제·DB/volume 삭제는 하지 않았다. 기본 A/B 외 임시 프로세스는 본인 PID만 확인해 종료했다.

실제 프론트 브라우저/10분 만료 갱신/다중탭·B5/native dialog 결과는 프론트 소유 검증과 결합해야 한다. TLS/shared환경·외부SSO·IdP·전체 키회전/복구 snapshot·JDBC 제한2정책 경쟁·실제 장애단절/commit 불명확·shared 로그 두파일 rollover/gzip/보관·전체8시간 대기는 미검증이다. 이번 실연동 통과로 이 항목이나 전체 운영 보안을 완료로 표시하지 않는다. 실기동 수정3건과 live 경쟁 판정은 조정자가 독립 재리뷰한다.

최종 잔류 조회: 각 검증PG core SQL1/local SQL2/개발키1행. Redis DBSIZE는12=16·13=10·14=5·15=68·0=0이었다(임시 TTL/진행 중 프론트 요청으로 이후 변동 가능). 기본 A/B의 csrf200을 재확인했다. 최초 fake fixture나 환경 오류를 DB/Redis 초기화로 지우지 않고 로그와 이력을 남겼다.


## 로컬 로그 개선 확인 (2026-10-29)

이번에는 로그를 읽기 쉽게 만들고, 개발에 필요한 안전한 SQL 파라미터를 확인하는 작업을 했습니다.
사용 중인 프론트3000/13000과 기본 A/B18080/18081은 종료하거나 교체하지 않았습니다.
새 migration·DB 초기화·개발 키 생성·비밀번호 변경·컨테이너 조작은 하지 않았습니다.

### 바꾼 내용

local에서는 Boot 기본 배너와 시작 정보를 다시 보여줍니다. 시작할 때는 START, 필수 초기화와 인증 준비·시작 작업이 끝나면 READY를 한 번 남깁니다.
실제 웹 서버 포트와 걸린 시간을 표시하며, 실패한 앱에는 READY가 나오지 않습니다. 정상 문맥 종료에는 STOP을 남깁니다.
색상은 Boot의 ANSI 지원 판단을 따르고 always/never로 선택할 수 있습니다. 색상을 꺼도 분류를 구분할 수 있습니다.
요청이 없는 로그에는 빈 traceId/actorUserId를 붙이지 않습니다.

Hikari·HTTP 서버 등 필요한 시작·연결·종료 메시지는 주소나 설정 원문을 꺼내지 않고 안전한 작업 설명으로 보여줍니다.
오류는 요약과 원인 종류·코드 위치를 여러 줄로 나눕니다. 긴 원인 체인은 최대32개를 확인하고 바깥쪽/안쪽 최대4개를 보여줍니다.
DB 원인의 SQLState는28P01/08001/08006/23505/42601만 표시하고, 실제 예외 메시지·SQLState 설명·자격은 출력하지 않습니다.
API 오류 본문의 비노출 기준과 공유 환경의 JSON 파일/회전 설정은 유지했습니다.

SQL은 Executor에서 별도 BoundSql을 만들지 않고, 실제 StatementHandler.parameterize에 전달된 BoundSql을 읽습니다.
SQL과 PARAMS를 나눠 JDBC 위치·속성명·Java/JDBC 타입·안전한 값을 표시합니다. SQL 물음표를 치환한 보기용 문장은 만들지 않습니다.
페이징·숫자 내부 ID·알려진 상태 코드·날짜만 값을 보여줍니다. 나머지는 값을 문자열로 만들기 전에 MASKED로 가리고,
인증·개발 키 SQL은 SQL과 파라미터를 함께 생략합니다. 임의 DTO getter를 다시 호출하거나 바이너리/스트림을 읽지 않습니다.
기존 불변 DTO와 안전한 Map 외 값은 UNAVAILABLE로 표시할 수 있습니다. 한 번에 최대32개와 본문 최대16000자 이내 설정을 적용하고 생략 표식을 붙입니다.
shared에는 상세 interceptor를 등록하지 않으며 JSON layout에서도 starter.sql 상세 내용을 다시 차단합니다.

### 이번에 수정한 파일

- 기동 상태: `common/config/LocalLifecycleLogging.java`(새 파일), `EnvironmentGuard.java`, `ServiceReadinessConfiguration.java`, `resources/application-local.yml`, `resources/META-INF/spring.factories`.
- 콘솔·오류 표시: `common/config/SafeLogLayout.java`, `common/web/ProblemResponses.java`.
- SQL 진단: `common/persistence/SqlDiagnostics.java`, `SqlParameterDisplay.java`(새 파일).
- 시험: `FoundationConfigurationTest.java`, `SafeLogContractTest.java`, `SqlDiagnosticsTest.java`(새 파일), `LocalLifecycleLoggingTest.java`(새 파일).
- 안내: `README.md`, `VERIFICATION.md`, `CHANGELOG.md`.

Java 경로는 src/main/java/com/example/starter, 시험은 src/test/java/com/example/starter 아래의 기존 패키지를 사용합니다. 루트/docs/frontend/infra는 수정하지 않았습니다.

### 확인한 결과

| 확인 방법 | 결과 |
| --- | --- |
| `test bootJar --offline` | 22 suites/67개 통과. 기존 기동·인증·CSRF·API 오류 비노출을 포함하며 실패/오류/skip은0개입니다. 새 로그 회귀10개가 포함됩니다. |
| SqlDiagnosticsTest6개 | 실제 XML foreach 추가 값과 DTO/Map·단일값/null의 바인딩 순서, 마스킹 표식, 잘못된 상태 문자열·위험한 이름·OUT·개수/길이 제한을 확인했습니다. 비밀 객체의 toString·getter·스트림을 읽지 않으며 실행용 SQL·객체를 그대로 유지했습니다. shared에는 상세 Bean이 없었습니다. |
| SafeLogContractTest4개 | 기존2개에 색상 on/off·빈 문맥 제거·안전한 프레임워크 요약·shared 상세 차단, 깊은 DB 원인/SQLState 검사를 추가했습니다. 비밀 표식과 원문 SQL 예외가 출력되지 않았습니다. |
| LocalLifecycleLoggingTest2개 | 실제 WebServer에서 얻는 포트, START→READY 순서·READY1회, 실패 뒤 READY 없음과 shared 무출력을 확인했습니다. |
| 실제 별도 앱18086/18087 | 기존 PG redis 검증DB/Redis15와 같은 키·정책을 사용했습니다. Flyway를 명시적으로 껐고 private 불변 JAR에서 실행했습니다. 색상off는 ANSI 없음·READY1회, on은 ANSI 있음·READY1회였습니다. 실제 포트를 각각 표시했습니다. |
| 별도 실패 앱18088 | 같은 검증DB에 잘못된 테스트 비밀번호로 연결했습니다. START 뒤 READY0건/ERROR 요약1건이었고 가장 안쪽 PSQLException·SQLState28P01을 확인했습니다. 테스트 비밀번호 표식은 로그에 없었습니다. |
| 실제 기본 인증 회귀 | 별도18086/18087에서 기존 liveAuthTest4개가 통과했습니다. 비밀번호 변경이나 다른 동시 로그인 정책 시험은 다시 실행하지 않았습니다. |
| 실제 조회·거부 응답 | 로그인200, 안전한 검색200/빈 결과, 사용자 foreach 조회200, 부서200, 잘못된 ID400, 잘못된 Origin403, 로그아웃204를 확인했습니다. 검색어의 CR/LF·가짜 READY 표식은 콘솔에 없었습니다. |
| 실제 읽기 전용 트랜잭션 | 로그를 켠 상태의 두 Mapper가 같은 pg_backend_pid와 Connection을 썼습니다. readOnly=true/autoCommit=false가 유지됐고 검색 결과도 같았습니다. TEMP/DDL이나 데이터 변경으로 검증하지 않았습니다. 정상 context.close의 STOP도 확인했습니다. |

OpenAPI는 기존13 paths/14 operations/28 schemas이며 SHA-256 `8ea277c75f3ebaf7ff1b0cada89c22c59557d400c64a4a52725926511cedf5f4`로 같습니다.
새 의존성·로깅 제품·테스트 플랫폼은 추가하지 않았습니다. Java formatter와 diff 검사를 확인했습니다.
private 로그에는 색상 on/off·실패·HTTP·읽기 전용 트랜잭션 결과를 나눠 보관했습니다.

### 콘솔 전후 예시

아래는 읽는 방법을 보여주는 테스트용 예시입니다. 임의의 시간·이름을 썼으며 실제 성공 횟수는 위 표에서 확인할 수 있습니다.

이전에는 시작 완료를 알기 어렵고 파라미터를 확인할 수 없었습니다.

```text
2026-10-29 07:00:00.000 INFO traceId= actorUserId= org.example.Framework framework_event
2026-10-29 07:00:01.000 DEBUG ... mapper_call ... SQL:
SELECT ... LIMIT ? OFFSET ?
```

이제 색상이 없어도 상태를 구분할 수 있습니다.

```text
2026-10-29 07:00:00.000 INFO  [START] lifecycle — starter-api — local 환경에서 시작합니다.
2026-10-29 07:00:03.250 INFO  [READY] lifecycle — starter-api — local, 포트 18086, 준비 완료 (3250ms)
2026-10-29 07:00:05.100 DEBUG [SQL] sql — UserMapper.selectUserList (4ms, SUCCESS)
  SQL:
    SELECT ... WHERE DISPLAY_NAME ILIKE ? LIMIT ? OFFSET ?
  PARAMS:
    1. pattern (String/AUTO) = [MASKED]
    2. size (int/AUTO) = 20
    3. offset (long/AUTO) = 0
2026-10-29 07:00:06.000 ERROR [ERROR] lifecycle — starter-api — 시작하지 못했습니다. 설정과 아래 원인을 확인하세요.
  가장 안쪽 원인: java.sql.SQLException (SQLState=28P01)
    at example.Driver.connect:10
```

### 남은 한계와 실행 상태

SQL을 실행값으로 치환하지 않으므로 PARAMS 번호를 같이 읽어야 합니다. AUTO는 MyBatis 타입 핸들러가 JDBC 처리를 선택한다는 뜻입니다.
임의 DTO의 계산 getter는 실행값에 영향을 줄 수 있어 진단에서 호출하지 않습니다. 새 DTO나 코드 값을 표시하려면 허용할 필드를 검토해야 합니다.
강제 OS 종료에서는 JVM 종료 후크가 실행되지 않을 수 있으므로 STOP 로그를 보장하지 않습니다.
다양한 실제 터미널의 색상 자동 감지와 공유 환경의 파일 회전/gzip/용량 실증은 이번에 새로 하지 않았습니다. 기존 미검증 항목을 완료로 바꾸지 않습니다.

이번에 만든18086/18087은 PID와 실행 사본을 확인한 뒤 종료했고, 실패18088과 읽기 전용 helper도 종료를 확인했습니다. 사용자 서버를 새 로그 버전으로 교체하지 않았습니다.
현재 실행본에 반영하려면 담당자가 본인이 만든 PID와 적용 시점을 확인해 재시작해야 합니다. 커밋/push는 하지 않았습니다.


## SECFILE — 요청 공격 경계와 이미지 파일 (2026-10-30)

저장소 루트에서 Temurin25/Gradle9.8.2로 다음을 실행했습니다.

```powershell
.\backend\gradlew.bat -p backend test bootJar --offline --console=plain
```

결과는29 suites/91 tests 중 **90개 통과**, 실패0·오류0·건너뜀1입니다.
건너뛴1개는 Windows에서 심볼릭 링크 생성 권한을 얻지 못한 DiskFileStoreTest입니다.
링크 방어 구현은 있지만 실제 링크 생성 시험까지 통과했다고 표시하지 않습니다.
기존 로그·인증·감사·독립 migration 계약 시험도 포함했습니다. 제품 앱은 기동하지 않았습니다.

| 요구사항 | 확인한 구현/증거 | 남은 실제 실행 |
| --- | --- | --- |
| SECFILE01 | 실제 FilterChainProxy에서 기본 StrictHttpFirewall의 세미콜론·경로 인코딩·상위 경로·NUL·TRACE·헤더 제어문자400, 안전 오류/trace | 프록시와 제품 컨테이너가 먼저 거부하는 경계 |
| SECFILE02/03/05 | JsonConfiguration customizer의 중복/미등록 필드·깊이, scalar query 인코딩 중복, 정상 한글/따옴표/SQL/스크립트 문자열 보존; 실제 MyBatis XML 파싱과 바인딩/소유자 조건/고정 정렬 | 새 파일 쿼리의 PostgreSQL 실행/왕복·경쟁 |
| SECFILE07 | 실제 JDK PNG/JPEG 디코딩/재인코딩과 메타데이터 제거, 위장/MIME/이름/픽셀·알 수 없는 길이 제한; 실제 보안 체인의 인증/CSRF 실패 전 parser 미실행 | 제품 서버의 정상 인증 업로드와 브라우저 회귀 |
| SECFILE07/10 | 임시 Tomcat의 실제 chunked multipart5MiB 허용, 초과/part2개/2KiB 초과 header 거부; 일반 JSON1MiB 유지 | 제품 배포 프록시/커넥터까지 연결한 요청 |
| SECFILE08/09 | 임시 디스크 저장/같은 핸들 읽기/삭제·덮어쓰기·경로/루트 교체 거부, 서비스 소유자 분리, MVC attachment/UTF-8/no-store/nosniff/length/204/스트림 닫힘 | Windows 실제 symlink/junction, 승인된 제품 루트 |
| SECFILE10 | 메모리 DB 대역과 실제 Spring transaction callback에서 예약 경쟁/100MiB/처리2개, 확정 rollback·commit 불명·잔류·삭제 실패/재시도·필수 감사 실패 | 실제 DB commit/연결 단절·디스크 장애와 동시 경쟁 |

새 migration은 core/V2__file_storage.sql만 추가했습니다. 기존5개 SQL SHA-256은 작성 전과 동일하고,
새 constraint20개는 이름 중복 없이 대문자/63바이트 제한을 통과했습니다. 새 SQL은 실제 적용하지 않았습니다.
FILE_OWNER_QUOTA/FILE_ASSET과 관련 Flyway 이력·제품 저장 파일은 이 작업에서 생성하지 않았습니다.
테스트가 사용하는 @TempDir과 임시 Tomcat 디렉터리는 제품 저장소와 다릅니다.

실제 Controller/DTO의 OpenAPI는16 paths/18 operations/32 schemas입니다.
출력은 `build/openapi/openapi.json`, SHA-256은
`fc7d791e5d550d6d56aba4586a25975680a7cf5b47057641145f5f401362c355`입니다.
필수 multipart file binary·201 FileInfo·Location, 페이지 응답, PNG/JPEG binary·다운로드 헤더와 DELETE204를 검사했습니다.
새 의존성은 추가하지 않았습니다. 기존5개 migration을 바꾸거나 커밋/push하지 않았습니다.

새 migration·제품 파일 루트 실행은 private SECFILE-EXEC-001에 대상 DB/명령/루트/용량/복구 영향을
구체화해 coordinator 검토 대상으로 전달했습니다. 기존 서버18080/18081·프론트13000·DB/Redis0·컨테이너는
이 작업에서 교체하거나 초기화하지 않았습니다. 브라우저 PC/모바일 업로드·실진행률·취소·키보드 검증과
프론트 XSS 출력 검수는 프론트 담당 증거가 필요합니다. 이 결과를 악성코드 전체 탐지·전체 ASVS 준수로 표현하지 않습니다.

### 최종 자체 검수 — 실행 안내 대조

MigrationMain은 환경변수만 읽으므로 단순 `migrateDatabase` 실행으로 local YAML의 DB 기본값이
전달되지 않는다는 점을 확인했습니다. README에 필수 프로파일·DB URL·사용자·비밀번호와 인증 저장소/
prelogin 선택을 모두 지정하는 Windows 예시를 보완했습니다. 공유 환경의 별도 DDL 자격과 TLS 요구도 유지했습니다.
APP_FILES_ROOT는 공유 앱 기동에 필수이고 migration CLI에는 필요하지 않습니다.
상대 파일 루트는 프로세스 작업 디렉터리 기준이며 bootRunLocal과 직접 JAR 실행의 차이를 명시했습니다.

이 자체 검수의 추가 변경은 문서뿐입니다. 앞선 `test bootJar --offline --console=plain`의
29 suites/91 tests(90통과/1환경 제외) 결과를 유지하며, 문서를 확인하기 위해 migration·앱 기동을 실행하지 않았습니다.
새 파일 테이블의 실제 PostgreSQL 왕복·동시 quota·commit/연결 단절·디스크 장애 복구와
심볼릭 링크 권한 시험, 실제 프록시/브라우저 파일 실연동은 계속 미검증입니다.
독립 리뷰의 판정과 SECFILE-EXEC-001 대상 실행 검토는 이 자체 검수와 별개입니다.


## CLR1-001 — HEAD 파일 핸들 해제 (2026-10-30)

독립 리뷰에서 실제 FileController의 GET 매핑이 HEAD에도 적용되고, ProblemDetailBoundary가
본문을 null로 바꾸면 Resource converter의 스트림 close가 실행되지 않는다는 문제를 확인했습니다.
기존 GET 컨트롤러 시험에는 이 advice가 등록되지 않아 문제가 드러나지 않았습니다.

HEAD 본문을 제거하기 전에 InputStreamResource의 열린 스트림을 명시적으로 닫도록 최소 수정했습니다.
파일 바이트를 읽지 않으며 기존 소유권·READY 검사·필수 READ 감사·안전 헤더는 그대로 적용합니다.
닫기 중 IOException도 원문 없는503으로 처리합니다. GET의 Resource converter 전송/close 경로는 유지했습니다.

FileControllerTest는 실제 컨트롤러와 ProblemDetailBoundary·ApiExceptionHandler를 함께 등록합니다.
반복 HEAD의 본문0/크기·형식/attachment·UTF-8/no-store/nosniff와 매 요청 open/close 수 일치,
STARTED/SUCCESS 감사, 다른 소유자404·안전 오류, 감사 실패503에서도 핸들 해제를 검사했습니다.
같은 설정의 GET은 실제 바이트와 핸들 닫힘을 확인합니다.

저장소 루트에서 실행한 명령은 다음과 같습니다. DB·제품 앱 기동은 없습니다.

```powershell
.\backend\gradlew.bat -p backend test --tests '*FileControllerTest' --offline --console=plain
.\backend\gradlew.bat -p backend test bootJar --offline --console=plain
```

전체29 suites/93 tests 중 **92개 통과**, 실패0·오류0·환경 제외1개입니다.
제외는 기존 Windows 심볼릭 링크 생성 권한 시험이며, 이를 통과로 올리지 않았습니다.
JAR 생성에 성공했고 OpenAPI16 paths/18 operations/32 schemas와 SHA-256
`fc7d791e5d550d6d56aba4586a25975680a7cf5b47057641145f5f401362c355`는 바뀌지 않았습니다.
기존5개 migration의 해시도 유지했습니다. 실제 파일 SQL·제품 저장 루트·DB/디스크 장애 복구와
브라우저 실연동의 미검증 상태는 앞선 SECFILE 기록과 동일합니다. 수정 후 독립 재리뷰 판정은 별도입니다.


## SECFILE-EXEC-001 — 승인된 실제 파일 연동과 최종 보완 (2026-10-30)

사용자가 대상·명령·영향·복구 계획을 승인한 뒤 수행했습니다. 앞선 SECFILE/CLR1의 “미실행”은
각 기록 당시 상태이며, 아래에서 확인한 항목만 실제 실행으로 갱신합니다.

### 대상과 빌드

전용 PostgreSQL `127.0.0.1:25432/starter_verify_20261029_jdbc`, JDBC 인증/allow,
prelogin=false, 기존 verify-20261029 세대와 개발 키를 사용했습니다. 신규 DB·사용자·비밀번호·키는 만들지 않았습니다.
명시한 외부 APP_FILES_ROOT를 두 검증 노드에서 공유했습니다. 동일 PC의 디스크 공유이며 다중 호스트 저장소 시험은 아닙니다.
새 A/B는18086/18087, 프론트 검증 Origin은 `http://localhost:13070`입니다.

실제 실행한 migration 명령은 다음과 같습니다. 프로파일/DB 자격/인증 저장소는 승인된 전용 대상으로 명시했습니다.

```powershell
.\backend\gradlew.bat -p backend migrateDatabase --offline --console=plain
.\backend\gradlew.bat -p backend migrateDatabase --offline --console=plain
```

core V2만 추가됐고 재실행은 추가 적용 없이 통과했습니다. 기존 core V1/auth/local history와 checksum,
합성 사용자2명·개발 키1개는 유지했습니다. 소스의 기존5개 migration SHA-256도 동일합니다.

최종 `test bootJar --offline --console=plain`은29 suites/96 tests 중 **95개 통과**, 실패0·오류0·환경 제외1개입니다.
제외는 기존 Windows symlink 생성 권한 시험입니다. 제품 저장소에 실제 clean을 실행하지 않았습니다.
local 기본 저장 경로 `./.private/files`가 build 밖이고 기존 `**/.private/` Git 제외 대상이며
APP_FILES_ROOT 명시값으로 재정의되는 것을 설정 회귀로 확인했습니다.
기존 `build/files`의 내용은 이동하거나 삭제하지 않았습니다.

최종 실행 JAR SHA-256은 `9ad3810db8422f6c89affa864fe2ad7ba6cb0fffb29e3aadbd9f099b8ddd9340`입니다.
실제 Controller 기반 OpenAPI16 paths/18 operations/32 schemas와 해시
`fc7d791e5d550d6d56aba4586a25975680a7cf5b47057641145f5f401362c355`는 그대로입니다.

### 실제 API·SQL·제한·복구 결과

- PNG/JPEG 업로드201, 메타데이터 제거, 한글 표시명과 크기/폭/높이, A에서 저장 후 B의 목록/바이트 조회를 확인했습니다.
- 다운로드 attachment·UTF-8 filename·길이·nosniff·no-store, 반복 HEAD200/본문0, 다른 소유자 GET/HEAD/DELETE404, 삭제204와 후속404를 확인했습니다.
- MIME/확장자 위장415, SVG415, 위장 내용400, 경로 파일명400, 디코딩 전 픽셀 상한400, part2개/파일 크기 초과413, query 중복/JSON 중복·미등록 필드400을 확인했습니다. SQL/태그 문자열 검색은 값으로 처리됐습니다.
- 소유자 분당 업로드 제한429/Retry-After를 그대로 기다렸고 bucket을 바꾸지 않았습니다. 각5,198,843바이트의 실제 이미지로 동시 예약을 시험했습니다. 시작98,778,017바이트에서 두 요청 중201/409로 한 건만 저장되어103,976,860바이트가 됐으며100MiB(104,857,600)를 넘지 않았습니다. 정확히100MiB를 채웠다고 표현하지 않습니다.
- 한 노드에 허용16M픽셀 이미지6개를 동시에 보내201 두 건, FILE_PROCESSING_BUSY429 네 건과 Retry-After를 확인했습니다.
- 전용 private fixture에서 실제 MyBatis/JDBC transaction을 사용했습니다. 파일 생성 전 쓰기 실패의 DB 보상, READY SQL 이후 `SELECT 1/0` 오류의 실제 PostgreSQL rollback/파일 정리, 삭제 실패의 DELETING commit/예약량 보존과 재시도 성공을 확인했습니다. 제품 장애 endpoint나 기존 테이블 변경으로 실패를 주입하지 않았습니다.
- 본 검증 manifest28개는 모두 삭제됐고 CREATE/DELETE 성공 감사가 각각 한 번입니다. rollback2개는 메타데이터와 성공 감사가 없습니다. HTTP 시험 파일은 해당 소유자의 DELETE API로 정리했고 fixture 데이터는 같은 서비스 삭제/보상 경로를 거쳤습니다.
- 우리 파일 정리 후에도 별도 검수의 manifest 밖 READY3개(8,218바이트)가 관찰됐습니다. 이 파일들은 삭제하지 않았고 해당 시점 소유자 사용량8,218바이트로 기록했습니다. 우리 manifest의 디스크 파일/행은 남지 않았습니다. 다른 검수와 동시에 작성되는 데이터까지0이라고 주장하지 않습니다.

### LIVE-CLOSE-001 — 쿠키 JWT의 CSRF 자동 제외 수정

실제 쿠키 인증에서 CSRF가 비어 있는데도 multipart 해석에 도달하는 결함을 발견했습니다.
설치된 Spring Security7.1.2 소스에서 Resource Server 초기화가 bearer matcher를 CSRF 제외에 추가하는
동작을 확인했습니다. 쿠키 resolver에서는 잘못된 access 문자열도 검증 전에 그 제외에 해당할 수 있습니다.
CsrfFilter의 완성 단계 ObjectPostProcessor에서 DEFAULT_CSRF_MATCHER를 복구했습니다.
단순 requireCsrfProtectionMatcher만 지정하면 자동 제외와 합성될 수 있어 그 방식으로 처리하지 않았습니다.

DB 없는 회귀에서 유효/만료/잘못된 access와 CSRF cookie/헤더 없음·잘못된 헤더, 파일 POST/DELETE,
login/refresh/logout/logout-all/password와 PATCH, 정상 XOR/GET/HEAD, Origin 누락/null/불일치를 검사했습니다.
선택 prelogin chain은 별도 CSRF filter를 유지하고 API 쿠키를 인증에 사용하지 않으며 실제 provider는 여전히 미도입입니다.

최종 A/B 실HTTP에서도63개의 cookie/CSRF 조합과 Origin3개를403으로 거부했습니다.
별도로 기존 개발 키로 메모리에서만 서명한 **실제 만료 JWT**를 만들었고, GET401 TOKEN_EXPIRED,
변경 경로18개 CSRF403, 정상 XOR을 보낸 뒤 만료 인증401의 순서를 확인했습니다.
개인키와 JWT 원문은 파일/출력으로 내보내지 않았습니다. 정상 로그인·refresh·logout도 실HTTP로 통과했습니다.

### 보존·현재 실행 상태·한계

최종 A/B 검증 서버는 프론트 검수를 위해 유지합니다. 기존18080/18081·프론트13000과
기본 DB/Redis0·다른 Redis DB·컨테이너/volume은 초기화하거나 교체하지 않았습니다.
새 보안 수정은 이 최종 A/B 사본에 반영됐으며 기존 사용자 서버에 자동 배포하지 않았습니다.
커밋/push는 하지 않았습니다. private 실행 로그·전후 DB snapshot·생성 manifest·감사 대조·HTTP/빌드 결과를 보관했습니다.

실제 symlink/junction 권한 시험, 전원 차단·실제 디스크 고갈·네트워크 단절에 의한 commit 불명,
재부팅 후 파일 내구성·다중 호스트 저장소·공유 환경의 운영 배포/로그 회전 실증은 미검증입니다.
commit 불명/잔류 실패의 보수적 처리와 열린 스트림 감사 실패는 DB 없는 실패 주입 결과와 구분합니다.
PC/모바일 브라우저 진행률·취소·재시도·키보드·직접 파일 선택/다운로드는 프론트 담당 증거가 필요합니다.
독립 리뷰의 최종 resolved 판정은 이 자체 실행 검증과 별개입니다.
