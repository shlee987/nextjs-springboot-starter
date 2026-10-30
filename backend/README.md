# 백엔드 시작하기

로그인, 사용자·부서 조회, 권한 검사, 감사 기록과 이미지 파일 API를 갖춘 Spring Boot 예제입니다.
Java 25 Temurin HotSpot, Spring Boot 4.1.2, Gradle Wrapper 9.8.2를 사용합니다.

## 로컬에서 실행하기

JDK 25를 `JAVA_HOME`과 IDE의 Project SDK·Gradle JVM에 지정하세요.
먼저 Docker 담당자가 준비한 PostgreSQL과 Redis가 켜져 있는지 확인한 다음,
backend 디렉터리에서 실행합니다. Windows에서는 `./gradlew`를 `.\gradlew.bat`으로 바꾸면 됩니다.

```sh
./gradlew bootRunLocal
```

| 항목 | 기본값 |
| --- | --- |
| API | http://127.0.0.1:8080 |
| 브라우저 주소 | http://localhost:3000 |
| PostgreSQL | jdbc:postgresql://127.0.0.1:25432/starter_local |
| Redis | 127.0.0.1:26379, DB 0 |
| DB·Redis 테스트 사용자 / 비밀번호 | testuser / 1234 |
| 로그인 테스트 계정 | localuser(USER), localadmin(ADMIN) |
| 로그인 비밀번호 | LocalExample1234! |

처음 실행하면 DB 테이블과 테스트 계정을 만들고 RSA3072 개발 키를 한 번 저장합니다.
파일 기능의 core V2도 적용하고 기본 파일 루트 `./.private/files`를 만들 수 있습니다.
이후 같은 DB를 쓰는 서버는 그 키를 다시 읽습니다. 기존 데이터를 지우거나 컨테이너를 시작하는 작업은 하지 않습니다.
AI가 실제 실행할 때는 대상 DB와 변경 범위에 대한 승인이 필요합니다.

다른 설정을 쓰려면 아래 환경변수를 IDE나 셸에 넣으세요. `.env.example`은 참고용이며 Spring이 자동으로 읽지 않습니다.
일반 `bootRun`과 JAR 실행에서는 `SPRING_PROFILES_ACTIVE`를 local/dev/stg/prod 중 하나로 직접 지정해야 합니다.
이미 사용 중인 포트의 프로세스는 자동으로 종료하지 않습니다.

API 포트를 바꾸려면 `SERVER_PORT`, 브라우저 주소를 바꾸려면 `APP_PUBLIC_ORIGIN`을 지정하세요.
예를 들어 브라우저가 `http://localhost:13000`이면 APP_PUBLIC_ORIGIN도 그 주소로 맞춰야 합니다.
파일 경로를 상대 경로로 지정하면 프로세스 작업 디렉터리를 기준으로 해석합니다.
`bootRunLocal`은 backend 디렉터리를 기준으로 실행하지만 직접 JAR을 실행할 때는 셸의 작업 디렉터리를 확인하세요.

## 콘솔에서 상태와 SQL 확인하기

local에서는 Boot 기본 배너가 먼저 나오고 `[START]`와 `[READY]`로 상태를 구분합니다.
`[READY]`는 DB·선택한 인증 저장소·서명 키 등 필수 준비와 시작 작업이 모두 성공한 뒤 한 번 나옵니다.
실제 열린 포트와 준비에 걸린 시간을 표시합니다. 배너나 HTTP 포트 준비 메시지만으로 성공을 판단하지 마세요.

다음은 형식을 설명하는 **테스트용 예시**입니다. 시간은 UTC이며 실제 실행 기록은 검증 문서에 있습니다.

```text
2026-10-29 07:00:00.000 INFO  [START] lifecycle — starter-api — local 환경에서 시작합니다.
2026-10-29 07:00:03.250 INFO  [READY] lifecycle — starter-api — local, 포트 18086, 준비 완료 (3250ms)
2026-10-29 07:00:05.120 INFO  [HTTP] RequestBoundaryFilter — GET /api/users → 200 (18ms) [trace=테스트용-ID]
2026-10-29 07:00:05.118 DEBUG [SQL] sql — UserMapper.selectUserList (4ms, SUCCESS)
  SQL:
    SELECT ... WHERE DISPLAY_NAME ILIKE ?
    LIMIT ? OFFSET ?
  PARAMS:
    1. pattern (Object/AUTO) = [MASKED]
    2. size (int/AUTO) = 20
    3. offset (long/AUTO) = 0
```

- 색상은 콘솔 지원 여부에 따라 자동으로 켜집니다. `SPRING_OUTPUT_ANSI_ENABLED=always`로 켜거나 `never`로 끌 수 있습니다. 색상을 꺼도 분류 표시가 남습니다.
- 요청이 있을 때만 trace와 사용자 ID가 붙습니다. 빈 필드를 모든 줄에 반복하지 않습니다.
- 오류는 안전한 요약, 원인 종류, 코드 위치를 여러 줄로 보여줍니다. 깊은 원인의 SQLState도 정해진 코드만 표시합니다. 예외 메시지·DB 자격·원문 SQL 오류는 출력하지 않습니다.
- root 레벨은 INFO입니다. SQL 전용 logger만 DEBUG로 동작하므로 전체 로그를 DEBUG로 바꿀 필요가 없습니다.

SQL은 실제 파라미터 설정에 사용한 BoundSql을 읽습니다. 물음표를 값으로 치환하지 않으며, PARAMS 번호가 JDBC 바인딩 순서입니다. 타입은 ParameterMapping의 Java/JDBC 타입이며 AUTO는 타입 핸들러가 JDBC 처리를 선택한다는 뜻입니다.
페이징·숫자 내부 ID·알려진 상태 코드·날짜만 표시하고 검색어·이름·이메일·로그인 ID·비밀번호·해시·토큰·키 등은 `[MASKED]`로 가립니다.
인증·개발 키 SQL은 SQL과 PARAMS 모두 `[AUTH_SQL_OMITTED]`로 생략합니다. 조회 결과는 출력하지 않습니다.

파라미터는 최대 32개, 이름 96자, 타입 64자이며 전체 상세 본문은 설정된 `app.logging.sql.max-chars`(최대 16000자)와 생략 표식으로 제한합니다.
스트림·바이너리를 소비하지 않습니다. 임의 DTO getter를 다시 호출하지 않고, 프로젝트의 알려진 불변 DTO와 안전한 Map만 읽습니다.
지원하지 않는 계산 값은 `[UNAVAILABLE]`, 허용하지 않은 값은 `[MASKED]`로 표시합니다. cache hit 등 바인딩까지 도달하지 않은 호출은 `[BINDING_NOT_REACHED]`로 표시합니다.
SQL 줄바꿈은 유지하고 제어문자는 이스케이프합니다. 실제 SQL·파라미터·타입 핸들러·연결·트랜잭션은 바꾸지 않습니다.

이 상세 출력은 local에서만 켜집니다. dev/stg/prod는 기존 JSON 파일과 slow 요약만 사용하며 상세 SQL과 PARAMS를 내보내지 않습니다.

## 빌드와 API 문서 만들기

```sh
./gradlew test bootJar
./gradlew test --tests com.example.starter.OpenApiExportTest --rerun-tasks
```

기본 `test`는 DB·Redis에 연결하지 않습니다. 실제 Controller와 DTO에서 springdoc 문서를 만들어
`build/openapi/openapi.json`에 저장합니다. 이 문서를 만드는 시험은 제품 앱이나 HTTP 포트, migration, 개발 키 생성을 실행하지 않습니다.
공개 docs에 문서를 복사하는 작업은 설계 담당자가, Orval 타입 생성은 프론트 담당자가 진행합니다.

`liveAuthTest`는 실제로 준비한 A/B 서버에 로그인·갱신·로그아웃 요청을 보냅니다.
기본 시험과 분리돼 있으며 실제 쓰기 승인과 대상 서버 확인 후에만 실행하세요.

버전은 Boot BOM·gradle.lockfile·Wrapper 설정으로 관리합니다. 의존성을 바꿀 때만
`./gradlew test bootJar --write-locks`로 lockfile을 갱신합니다. 라이브러리별 수동 체크섬 관리나 새 로깅 제품은 추가하지 않았습니다.
Bouncy Castle은 Boot4.1.2 BOM에 없어 [공식 안정판1.86](https://www.bouncycastle.org/download/bouncy-castle-java/)을 직접 지정했습니다.

## 환경변수와 저장소 선택

공유 환경에서는 테스트용 자격·개발 키·seed를 사용할 수 없습니다. HTTPS origin, DB verify-full,
Redis 사용 시 TLS·ACL을 설정해야 합니다. 실행 DB 계정은 감사 테이블 SELECT/INSERT만 사용하고 DDL·감사 UPDATE/DELETE 권한을 갖지 않아야 합니다.

| 환경변수 | 용도 |
| --- | --- |
| APP_PUBLIC_ORIGIN | 정확한 단일 origin, local loopback만 HTTP 예외 |
| DB_URL / DB_USERNAME / DB_PASSWORD | 공유 환경에서 필요. 자격 내장 URL과 TLS 검증 우회 금지 |
| APP_AUTH_STATE_STORE | redis(기본값) 또는 jdbc, memory/fallback/이중 쓰기 없음 |
| REDIS_HOST / REDIS_PORT / REDIS_USERNAME / REDIS_PASSWORD | Redis 모드만 요구, 공유 기본 port6379 |
| APP_AUTH_NAMESPACE_GENERATION | 기본 v1, snapshot 복구 시 새 세대로 변경하고 전체 재로그인 |
| APP_AUTH_CONCURRENT_LOGIN | allow(기본값), deny-new, replace-old |
| APP_AUTH_JWT_ISSUER / APP_AUTH_JWT_AUDIENCE | 공유 환경에서 필요, local urn:starter:local / starter-api-local |
| APP_AUTH_JWT_ACTIVE_KEY_ID | 공유 active kid |
| APP_AUTH_JWT_PRIVATE_KEY_LOCATION | 공유 private RSA JWK JSON 파일 경로, RS256·RSA>=3072 |
| APP_AUTH_JWT_PUBLIC_KEYS_LOCATION | 공유 public JWKS 파일 경로. private key 포함 금지 |
| APP_AUTH_PRELOGIN_SESSION_ENABLED | 기본 false, 외부 로그인 임시 HttpSession만 조건부 활성 |
| APP_AUDIT_LOGIN_REFERENCE_KEY_LOCATION | 공유 환경에서 필요 별도 secret 파일(최소32byte), HMAC-SHA256용 |
| APP_AUDIT_RETENTION_DAYS | 공유 환경에서 필요 양수. 자동 대량 삭제는 수행하지 않음 |
| APP_SERVER_ID | 선택 외부 노드명, 영숫자/점/밑줄/하이픈 1~100자, 기본 host·port·시작 UUID |
| APP_LOGGING_DIRECTORY | 기본 ./logs |
| APP_FILES_ROOT | local 기본 ./.private/files. 공유 앱 기동에는 웹 루트 밖의 전용 절대 경로 필수 |
| SERVER_PORT | API 포트, 기본8080. 기존 사용 중인 포트를 교체하지 않음 |
| SPRING_DATA_REDIS_DATABASE | Redis DB 번호, 기본0. 검증 환경에서는 승인된 별도 DB 번호 지정 |

기본 수명은 access10분, 미사용30분, 전체8시간입니다. 시계 오차는30초(설정 범위0~60초), CSRF8시간, prelogin10분입니다.
로그인 실패는 계정5회/15분·잠금15분과 IP30회/분을 각각 셉니다. 일반 API는 익명 IP60회/분·인증 사용자120회/분으로 제한합니다.
원문 로그인 ID와 IP는 제한용 키에 넣지 않습니다.

JDBC 모드는 Redis에 연결하지 않습니다. prelogin=false면 Spring Session 저장소·필터·정리 작업을 만들지 않습니다.
true일 때만 선택한 Redis 또는 JDBC Session을 쓰며 별도 JSON codec으로 저장합니다.
실제 외부 IdP와 provider 타입은 아직 연결하지 않았습니다.

같은 서비스의 노드는 키·issuer/audience·인증 정책·감사 키가 같아야 합니다. 다르면 준비 단계에서 중단합니다.
정책을 바꿀 때 기존 digest만 지워 로그인을 유지하지 마세요. 전체 로그아웃과 정책 전환을 함께 진행해야 합니다.

## DB 테이블 적용하기

`FoundationMigrations`가 아래 순서로 적용하고 각각의 history를 보관합니다.

| 경로 | 적용 조건 | history |
| --- | --- | --- |
| db/migration/core | 모든 모드 | public.flyway_schema_history |
| db/migration/auth-jdbc | JDBC | public.auth_jdbc_schema_history |
| db/migration/session-jdbc | JDBC+prelogin | auth_session.flyway_schema_history |
| db/migration/local | local | public.local_seed_schema_history |

local 앱만 자동으로 적용합니다. 공유 환경에서는 자동 적용을 끄고 운영자가 전용 DB 자격과 승인된 대상에서 실행합니다.

아래는 **승인된 로컬 DB에 적용할 때의 예시**입니다. backend 디렉터리에서 실행하며,
`migrateDatabase`는 `.env.example`이나 application-local.yml의 DB 기본값을 읽지 않습니다.
네 필수 환경변수를 모두 지정해야 합니다. 인증 저장소는 실제 앱과 같은 값을 사용하세요.

```powershell
$env:SPRING_PROFILES_ACTIVE='local'
$env:DB_URL='jdbc:postgresql://127.0.0.1:25432/starter_local'
$env:DB_USERNAME='testuser'
$env:DB_PASSWORD='1234'
$env:APP_AUTH_STATE_STORE='redis'
$env:APP_AUTH_PRELOGIN_SESSION_ENABLED='false'
.\gradlew.bat migrateDatabase
```

macOS/Linux에서는 같은 값을 `export 이름=값`으로 지정한 다음 `./gradlew migrateDatabase`를 실행합니다.
공유 환경에서는 프로파일·TLS를 검증하는 DB URL·전용 migration 자격으로 바꿔야 합니다.
위 명령은 실행한 셸의 환경을 바꾸므로, 다음 앱 기동 전에 대상과 자격을 다시 확인하세요.
JDBC 인증은 APP_AUTH_STATE_STORE=jdbc, 임시 Session도 사용하면 APP_AUTH_PRELOGIN_SESSION_ENABLED=true를 지정합니다.

이 명령은 SQL만 적용하며 웹 서버·파일 루트·서명 키 생성을 실행하지 않습니다. local seed는 적용합니다.
APP_FILES_ROOT는 앱 기동에 필요한 값이며 이 migration 전용 명령에는 필요하지 않습니다.
현재 core V2는 FILE_OWNER_QUOTA/FILE_ASSET과 제약·인덱스를 추가합니다. 기존 DB에 실행해도 새 스키마 쓰기입니다.
적용한 SQL 파일은 수정하지 말고 다음 버전을 추가하세요. 재귀 경로 `db/migration`을 기존 locations에 함께 넣지 않습니다.

동시 runner는 별도 연결1개의 DB transaction advisory lock으로 순서를 지킵니다.
core가 채워진 public에 auth/local history를 처음 만들 때는 기존 이력의 checksum·완료 여부와 알려진 테이블·인덱스·시퀀스를 확인합니다.
낯선 객체나 충돌이 있으면 중단합니다. 검증에 통과한 **새 history만 baseline0**으로 초기화해 V1/localV2를 빠뜨리지 않습니다.
`baselineOnMigrate=false`, `cleanDisabled=true`를 유지하며 기존 history를 덮어쓰거나 지우지 않습니다.
실패한 경우 완료된 단계는 보존하고 원인을 해결한 뒤 같은 명령으로 재개하세요.
선택 Session을 제거할 때도 모든 노드의 사용 종료와 백업을 확인하고 별도로 승인받아야 합니다.

## API를 사용할 때

코드는 common, auth, user, department, permission, audit, file로 나뉩니다. Controller는 입력과 응답을,
Service는 권한·업무·트랜잭션을, Mapper는 SQL을 처리합니다.
성공 응답은 ApiResponses로 만들고 실패 응답은 고정된 Problem Details 형식만 반환합니다. 실제 예외·SQL·입력 원문은 보내지 않습니다.

- API는 RS256 JWT 쿠키와 공유 로그인 상태를 매 요청 확인합니다. 권한은 JWT scope가 아닌 저장소의 snapshot을 사용합니다. HttpSession만으로 API 인증을 할 수 없고 관리자도 명시된 권한만 가집니다.
- 정확한 GET csrf와 POST login/refresh/logout만 선행 JWT 검증에서 제외합니다. `/auth/external/**`는 선택 세션 구조만 준비돼 있습니다.
- refresh는 CSPRNG32byte 난수이며 SHA-256만 저장합니다. 한 번 사용한 값은 회전하고 재사용을 발견하면 해당 sid를 폐기합니다. 계정·비밀번호·역할 변경은 관련 로그인을 모두 폐기합니다.
- 자격 원문은 HttpOnly Set-Cookie로만 보냅니다. local은 local-*, 공유 환경은 __Host-* 이름을 쓰며 SameSite=Lax, Path=/, Domain 없음입니다. Secure의 예외는 local의 loopback HTTP뿐입니다.
- GET /api/auth/csrf로 받은 XOR token을 X-CSRF-TOKEN에 넣으세요. 쿠키 JWT가 있어도 모든 변경 요청에서 CSRF를 검사합니다. login/logout 뒤 다시 조회해야 합니다. refresh와 일반 JWT 조회는 CSRF를 교체하지 않습니다.
- 쓰기 요청에는 정확한 Origin 또는 Referer origin이 필요합니다. 누락/null/불일치는403입니다. 401에서 쿠키를 지우지 않고 204/HEAD에는 본문이 없습니다.
- 브라우저 주소는 localhost로 통일하세요. API 포트에서 Swagger를 직접 열면 설정된 프론트 origin과 달라 쓰기가 거부될 수 있습니다. 실제 프록시 환경이나 읽기 호출로 확인하며 이를 위해 검증을 끄지 않습니다.

날짜는 UTC의 날짜·초·밀리초3형식이고 연도0001~9999를 엄격히 검사합니다. JWT NumericDate·HTTP Date·Session epoch는 표준을 유지합니다.
큰 ID와 BigDecimal은 문자열, 페이지와 count는 안전 정수, 빈 목록은[], 일반 null은 그대로 반환합니다.
목록은 page0/size20(최대100), q최대100자이며 LIKE 문자를 escape합니다. 정렬은 enum과 PK로 고정하고 감사 조회는 최대31일입니다.

## 공유 로그와 감사 기록

공유 환경은 logs/{환경}/{serverId}/application.log(INFO 이상)와 error.log(ERROR만)에 JSON을 씁니다.
날짜/100MB 단위 회전, gzip, 30일 보관이며 archive 용량은3GB/1GB입니다. 활성 파일까지 포함한 엄격한 디스크 상한은 아닙니다.
쓰기 불가능한 경로에서는 준비 완료로 진행하지 않습니다. local에는 파일 appender가 없습니다.

중요한 조회는 독립된 STARTED와 종료 이력을 확인한 뒤 결과를 보냅니다. 감사 저장이나 commit이 실패하면503이며,
일반 BEST_EFFORT 감사 실패는 업무 결과를 유지하고 안전한 오류만 남깁니다. 중요한 변경의 성공 감사는 업무와 같은 트랜잭션에 저장합니다.
두 감사의 CLIENT_IP는 정규화한 직접 peer만 사용하고 Forwarded는 기본 신뢰하지 않습니다. HTTP가 없는 SYSTEM 기록은NULL입니다.
공개 DTO와 진단 로그에는 원문 IP·UA·login reference를 넣지 않습니다. login reference는 전용 HMAC이며 공유 환경의 별도 secret으로 관리합니다.

## 장애 후 복구할 때

Redis의 권한 변경은 역할→사용자 순서로 잠근 뒤 영속 차단 표식(fence)을 남기고 DB 변경과 필수 감사를 commit합니다.
모든 로그인 폐기를 확인한 다음 같은 operationId로만 차단을 풉니다. 실패한 차단을 TTL로 풀거나 자격을 복원하지 않습니다.

승인된 대상에서 다음 순서로 확인하세요.

1. operationId와 ACCESS_AUDIT의 SUCCESS를 보고 DB commit 여부를 확인합니다. 불명확하면 차단을 유지합니다.
2. 해당 operation이 차단한 사용자와 현재 DB 상태를 확인합니다. 다른 operation의 차단은 건드리지 않습니다.
3. 영향을 받은 로그인을 모두 다시 폐기합니다. Redis는 user-state.lua revokeAll, JDBC는 guard 잠금 안의 전체 폐기를 사용합니다.
4. 모든 폐기를 확인한 뒤 같은 operationId로 unblock합니다. 일부가 실패하면 해제를 미룹니다.

오래된 snapshot을 이전 자격과 함께 재개하면 안 됩니다. 진입 차단과 백업을 확인한 뒤 승인된 인증 상태 초기화,
키 회전·구키 차단·전체 재로그인을 진행하고 APP_AUTH_NAMESPACE_GENERATION을 새 값으로 바꿉니다.
Redis 기본 실제 prefix는 starter:{환경}:auth:v1이며 정책 anchor는 세대 밖의 starter:{환경}:auth:policy입니다.
JWT의 asg와 세대로 분리한 refresh hash는 JDBC에서도 이전 자격 사용을 막습니다. JDBC의 이전 로그인/history·bucket은 승인된 절차로 정리하고,
guard 차단은 DB 변경 결과를 확인해 operation별로 복구하세요. 모든 노드의 세대와 키를 맞춘 뒤 서비스를 재개합니다.
자동 outbox·대량 감사 삭제·운영 복구 endpoint는 제공하지 않습니다.

## 이미지 파일 올리기

인증된 사용자는 PNG/JPEG를 올리고 본인 파일만 조회·다운로드·삭제할 수 있습니다.

| 요청 | 결과 |
| --- | --- |
| `POST /api/files` | multipart `file` 하나, 성공201과 FileInfo·Location |
| `GET /api/files?page=0&size=20` | 본인의 완료 파일 목록, size 최대100 |
| `GET /api/files/{fileId}/content` | 이미지 바이트, attachment·UTF-8 이름·no-store·nosniff |
| `DELETE /api/files/{fileId}` | 삭제와 용량 반환을 확인한 뒤204 |

다운로드 경로의 HEAD도 동일한 소유권·필수 감사와 안전 헤더를 적용하고 본문은 보내지 않습니다.
HEAD에서 열린 파일 핸들은 본문 처리 advice가 명시적으로 닫습니다. GET은 전송 후 Resource converter가 닫습니다.

다른 사람 파일과 없는 파일은404입니다. owner·저장 경로·상태는 요청으로 받지 않습니다.
업로드 요청에도 기존 Origin·CSRF 헤더와 인증 쿠키가 필요합니다. CSRF를 form field로 보내지 마세요.
파일 하나는5MiB, multipart 전체는5MiB+64KiB입니다. 일반 JSON은 계속1MiB입니다.
서버는 확장자·MIME·시그니처를 확인한 다음 디코딩 전에 각8192px·총16,000,000픽셀을 검사합니다.
정상 디코딩한 이미지를 PNG/JPEG로 다시 만들어 메타데이터를 제거하며 결과도5MiB로 제한합니다.
소유자별100MiB는 재인코딩 후 저장 바이트 기준이며 예약 중·삭제 중 파일도 포함합니다.
조건부 DB 갱신으로 동시 예약을 제한하고, 업로드는 소유자별 분당10회·서버별 동시에2개를 처리합니다.
대기열 없이429와 Retry-After로 응답합니다. 취소·통신 실패 후에는 목록에서 실제 저장 여부를 확인하세요.

local 기본 경로는 Git에서 제외한 `./.private/files`입니다. 공유 환경은 `APP_FILES_ROOT`에
웹 정적 폴더 밖의 전용 절대 경로를 지정해야 합니다. 앱 시작 시 루트를 만들 수 있으므로
새 환경의 실제 실행은 migration과 디스크 변경 영향을 먼저 확인하세요.
기본 루트는 빌드 산출물 밖에 있어 Gradle clean으로 지워지지 않습니다. 기존 `build/files`의 파일을 자동 이동하거나 삭제하지 않습니다.
기존 데이터를 보존하려면 기존 APP_FILES_ROOT를 명시하고 별도 백업·DB 메타데이터 대조 후 경로 이전을 계획하세요.
새 루트는 POSIX700 또는 Windows 소유자 전용 ACL로 만들고 기존 루트의 권한은 임의로 바꾸지 않습니다.
생성 UUID 키만 경로로 사용하며 링크·junction·기존 파일 덮어쓰기를 거부합니다.
Windows 기본 NIO의 fileKey가 없으면 생성시각과 ACL로 교체를 확인합니다. 관리자나 앱과 같은 OS 계정의
경쟁 파일 변경까지 방어하는 불변 파일 식별자가 아니므로 상위 디렉터리도 신뢰된 운영자가 보호해야 합니다.
로컬 디스크만으로 여러 서버의 저장소를 공유할 수 없습니다. 공유 배포에는 모든 노드가 접근 가능한 저장소와
운영 검증이 필요합니다. 이번에는 cloud SDK·바이러스 검사·CDR를 추가하지 않았습니다.
파일 형식 검증은 악성코드 전체 탐지를 뜻하지 않습니다. SVG·HTML·PDF·Office·압축파일은 받지 않습니다.

주요 오류는 이름/내용400, 크기413, 형식415, 용량409, 처리/횟수429, 디스크/DB/필수 감사503입니다.
이름·경로·바이트를 오류 응답과 진단 로그에 복사하지 않습니다. 요청의 SQL 단어나 HTML 태그를 일괄 삭제하지 않습니다.
실제 StrictHttpFirewall, JSON 중복/미등록 필드 거부, 단일 query 중복 거부와 바인딩 SQL을 함께 적용합니다.
현재 API에는 query 배열 계약이 없습니다. 배열을 추가할 때는 해당 이름에 대한 명시적 예외와 회귀 시험이 필요합니다.

### 파일 처리 중 장애가 났을 때

파일 테이블은 새 core V2 migration에서 추가합니다. 기존 적용 SQL5개의 내용은 바꾸지 않았습니다.
검증된 이미지의 용량·PENDING 행을 먼저 예약하고, 임시 파일과 새 최종 파일을 작성한 후
READY와 필수 성공 감사를 함께 commit해야 공개합니다. 확정 rollback이면 새 파일을 정리하고 예약량을 반환합니다.
삭제는 먼저 DELETING으로 숨기고 파일을 지운 다음 메타데이터·예약량·성공 감사를 같은 transaction에서 반영합니다.
디스크 오류나 commit 결과 불명에서는 성공으로 응답하거나 용량을 추측으로 반환하지 않습니다.
PENDING/DELETING과 잔류 파일은 숨긴 채 용량에 남을 수 있습니다. 자동 재귀 청소 작업은 없습니다.

운영자는 관련 생성ID와 DB 상태·감사 성공 여부·실제 파일을 대조하고 해당 저장소의 쓰기를 멈춘 뒤 복구를 결정해야 합니다.
PENDING은 최종 commit 여부를 확인한 뒤 보존하거나 파일/행/예약량을 함께 정리합니다.
DELETING은 같은 소유자의 DELETE 재시도로 마무리할 수 있습니다. READY는 정상 파일 존재·크기를 확인합니다.
원인을 확인하지 않은 상태에서 quota를0으로 덮어쓰거나 디렉터리를 통째로 지우면 안 됩니다.
응답 스트리밍을 시작한 뒤 네트워크가 끊기면 이미 보낸200을 오류로 바꾸지 못하며 클라이언트가 전송 실패를 확인해야 합니다.
승인된 별도 검증 DB에 새 migration을 적용하고 전용 파일 루트에서 실제 API 왕복·동시 용량·오류 복구를 확인했습니다.
기존 기본 서버를 이 버전으로 교체하거나 운영 배포한 것은 아닙니다. PC/모바일 브라우저 실연동 결과는 프론트 담당 검증과 구분합니다.

## 확인한 범위

DB 없는 시험, 승인된 격리 실연동, 로컬 로그 검사를 구분해 [검증 기록](VERIFICATION.md)에 정리했습니다.
사용 중인 기본 서버와 기존 DB를 이번 로그·파일 구현 작업 때문에 바꾸지 않았습니다.
자세한 설계는 [공통 기반](../docs/implementation/backend-foundation.md), [인증](../docs/implementation/authentication-foundation.md),
[DB](../docs/implementation/backend-data-model.md), [로그](../docs/implementation/backend-observability.md),
[추가 로그 기준](../docs/implementation/readability-and-local-logs.md),
[요청 방어와 파일 계약](../docs/implementation/attack-defense-and-files.md)을 참고하세요.
