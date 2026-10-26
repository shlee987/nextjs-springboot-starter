# API 기반

Java 25, Spring Boot 4.1.2, Gradle Wrapper 9.8.2를 사용한다. 정확한 선정 버전은
[버전표](../docs/implementation/stack-versions.md), 응답 형태는
[API 계약](../docs/implementation/api-contract.md)이 원본이다.

## 로컬 실행

Docker 담당이 `starter-project`의 PostgreSQL·Redis를 준비한 뒤 backend 디렉터리에서 실행한다.
Temurin 25를 프로젝트/Gradle JDK로 선택하면 DB 환경변수나 자격 파일 없이 시작할 수 있다.

```sh
./gradlew bootRunLocal
```

Windows 명령은 `.\gradlew.bat bootRunLocal`이다. 이 task만 `local` 프로파일을 선택한다.
일반 `bootRun`과 JAR 실행에는 프로파일을 명시해야 하며 자동 local fallback은 없다.
`dev`는 공유 개발 환경이고 개인 PC 실행은 `local`이다.

| local 기본값 | 값 |
| --- | --- |
| PostgreSQL URL | jdbc:postgresql://127.0.0.1:25432/starter_local |
| DB 사용자/비밀번호 | testuser / 1234 |
| Redis | 127.0.0.1:26379, DB 0, testuser / 1234 |
| 브라우저 origin / API | http://localhost:3000 / http://127.0.0.1:8080 |

위 자격은 공개된 가짜 local 테스트 값이다. 공유 환경에는 사용하지 않는다.
`application-local.yml`만 기본값을 가지며 `DB_*`, `REDIS_*`, `APP_PUBLIC_ORIGIN` 환경변수로
바꿀 수 있다. 포트를 바꿨다면 Docker 준비 값과 함께 맞춘다. `.env.example`은 override 예제이며
Spring이 자동으로 읽는 파일이 아니다. local 앱도 루프백 DB·Redis만 사용한다.

IntelliJ에서는 `backend`를 Gradle 프로젝트로 열고 Project SDK와 Gradle JVM을 Temurin 25로
한 번 맞춘다. Gradle 창의 `application > bootRunLocal`을 실행하면 된다.
일반 Java Application 실행 구성을 선호하면 main class를
`com.example.starter.StarterApplication`, classpath를 backend main 모듈로 지정하고
환경변수에 `SPRING_PROFILES_ACTIVE=local`만 넣는다. 별도 Spring/Ultimate 플러그인은 필요 없다.
기존 8080 프로세스를 자동 종료하지 않으며 포트 충돌 시 실행 설정의 `SERVER_PORT`를 변경한다.

## 빌드와 격리 검사

[Eclipse Temurin 공식 배포본](https://adoptium.net/temurin/releases/?version=25)에서 사용하는
OS·아키텍처에 제공되는 정식 Temurin 25 LTS HotSpot JDK를 확인하여 설치하고 실제 패치·build
번호를 기록한다. OS별 패치 제공 시차가 있으므로 전체 OS에 25.0.5+8이 제공된다고 가정하지 않는다.
`JAVA_HOME`·IDE 프로젝트 JDK·Gradle JVM을 같은 설치본으로 맞춘다. Gradle toolchain은
Java 25와 `JvmVendorSpec.ADOPTIUM`을 요구한다.
시스템 JDK를 바꾸지 않고 현재 터미널에만 `JAVA_HOME`을 설정해도 된다. 자동 JDK 다운로드는 없다.
다음 명령은 backend 디렉터리에서 실행한다. Windows는 `./gradlew` 대신 `.\gradlew.bat`을 쓴다.

```sh
./gradlew test bootJar
./gradlew dependencies --configuration testRuntimeClasspath
```

`test`는 JUnit Jupiter와 최소 Spring 테스트 모듈을 사용하며 실제 DB·Redis에 접속하지 않는다.
테스트 전용 합성 Controller는 제품 JAR에 포함되지 않는다. Actuator·JPA·Querydsl·SSO SDK·
Testcontainers·Spring Security Test·AssertJ·Mockito·Awaitility는 기본 의존성이 아니다.

Boot native BOM과 `gradle.lockfile`로 의존성 버전을 고정하고, Wrapper의
`distributionSha256Sum`으로 Gradle 배포본 무결성을 확인한다. 라이브러리는 HTTPS Maven Central에서
해석한다. 의존성 변경 시에만 아래 명령으로 잠금 파일을 갱신하고 버전·전이 의존성 변경을 검토한다.

```sh
./gradlew test bootJar --write-locks
```

IntelliJ의 소스 다운로드에 라이브러리별 수동 체크섬 등록·승인 절차를 요구하지 않는다.
취약점·지원 종료·라이선스 검토와 공개 저장소의 비밀값 보호 기준은 계속 적용한다.

springdoc의 Swagger 의존성은 Jackson 2를 전이 의존성으로 사용한다. 앱 HTTP JSON과 Session JSON은
Jackson 3을 사용하며 Jackson 2로 교체하지 않는다. 세부 그래프는 lockfile을 확인한다.

## 외부 설정과 실행

`SPRING_PROFILES_ACTIVE=local|dev|stg|prod` 중 하나를 반드시 명시한다. 공통 YAML → 선택한
프로파일 YAML을 기본으로 Spring의 외부 설정 우선순위가 적용되며, 환경변수·system property·
명령행 설정의 최종값을 `EnvironmentGuard`가 검사한다. `.env`는 Spring에서 자동 로드되지 않는다.
공유 `dev/stg/prod`는 DB·Redis·origin의 외부 설정이 모두 필요하며 local 기본값을 상속하지 않는다.

| 입력 | 사용 |
| --- | --- |
| SPRING_PROFILES_ACTIVE | 단일 배포 환경. 누락·지원하지 않는 값·복수 선택 거부 |
| APP_PUBLIC_ORIGIN | 단일 origin. local 루프백 HTTP 외에는 HTTPS |
| DB_URL | PostgreSQL JDBC URL. 자격 내장 금지, local 외 `sslmode=verify-full` |
| DB_USERNAME / DB_PASSWORD | 공유 환경의 DML 앱 계정. local은 공개 테스트 자격 |
| REDIS_HOST / REDIS_PORT | 외부 Redis 접속점, local은 루프백 |
| REDIS_USERNAME / REDIS_PASSWORD | 공유 환경의 전용 ACL 사용자. local은 공개 테스트 자격 |

PostgreSQL의 별도 CA는 외부 `sslrootcert` 설정을 사용한다. Redis는 local 이외 TLS를 켜며
JVM 신뢰 저장소 또는 Boot SSL bundle로 승인 CA를 외부 주입한다. hostname·인증서 검증을 끄는
factory·trust-all 설정을 추가하지 않는다. 실제 인증서·topology 검증은 배포 단계에서 수행한다.
프로파일과 TLS 정책은 운영 주소나 인증서를 예제로 내장하지 않는다.

앱 포트는 기본 8080이다. local은 `127.0.0.1`에 바인딩하며 브라우저는 Next의 같은 출처 `/api`
전달 경로를 사용한다. CORS 예외 목록은 열지 않는다. 운영 프록시·backend 접근 경계는 배포 설계
후 적용한다. 현재는 forwarded header를 신뢰하지 않고 HSTS는 TLS 종료 프록시가 소유한다.

일반 `bootRun`/JAR은 실행 환경에서 프로파일과 필요한 외부 값을 명시한다.

```sh
./gradlew bootRun
# 또는 빌드한 JAR을 선택하여 java -jar build/libs/starter-api-0.1.0-SNAPSHOT.jar
```

기동 시 `ServiceReadinessConfiguration`은
DB의 읽기 전용 연결 유효성과 Redis probe 세션의 저장·복원·삭제를 확인한다. Redis에 쓰기가
발생하므로 AI의 기동은 [DB 실행 규칙](../docs/common-rules/docs/conventions/common/database-access.md)
승인 대상이다. 실패하면 웹 서버 시작을 완료하지 않으며 로컬 메모리 Session으로 대체하지 않는다.
probe는 30초 유휴 수명을 가지며 저장소 장애 중 부분 저장된 키·index의 정리는 통합 시험에서 확인한다.

## Migration 관리

Flyway SQL의 소유자는 backend다. 설계가 확정된 테이블·인덱스·제약·필수 초기 데이터는
`src/main/resources/db/migration`의 버전 있는 SQL로 관리한다. 현재는 업무 SQL·seed가 없으며
가짜 migration이나 테이블은 만들지 않는다. 새 SQL은 기능 설계 후 Flyway 버전 파일명 규칙으로
추가하고, 적용한 버전은 수정하지 않고 다음 버전으로 변경한다.

local은 같은 테스트 계정으로 Flyway를 기동 시 자동 실행한다. 빈 migration 위치도 허용한다.
Flyway 12.4.0에서 migration 0개로 시작할 수 있고 첫 기동에 `flyway_schema_history`가 생성된다.
`No migrations found` 경고는 현재 SQL이 없는 상태를 뜻하며 업무 schema 준비 완료를 의미하지 않는다.
앱을 다시 시작하면 기존 volume·적용 이력을 유지한다. 이력이나 volume 삭제로 오류를 자동 복구하지 않는다.
공통 설정과 `dev/stg/prod`는 `spring.flyway.enabled=false`를 유지하며,
실제 환경의 DDL은 앱과 분리한 전용 migration 자격·승인된 배포 절차에서만 적용한다.

## 코드와 후속 기능의 경계

- `web`: 명시적 `ApiResponses.ok/created/noContent`, `ApiResponse`, `PageData`, 등록 오류 정의,
  MVC·Security·필터·Servlet `/error`·직접 ProblemDetail 반환의 공개 오류 허용목록.
- `config`: 환경 검증·기본 거부 Security·Session JSON/쿠키·동일 DataSource 트랜잭션·JSON 제한.
- 성공을 자동 래핑하지 않는다. 서비스는 업무 DTO를 반환하며 HTTP 래퍼는 Controller에서 사용한다.
- `fieldErrors`는 요청 DTO에 직접 선언한 필드만 `REQUIRED/INVALID_FORMAT/OUT_OF_RANGE/INVALID_LENGTH`로
  매핑한다. 중첩 필드·메서드 제약의 공개 경로는 해당 기능의 DTO 계약에서 확정한다.
- 오류는 `application/problem+json`, 동일 서버 UUID v4의 `traceId`·`X-Request-ID`, `no-store`를 사용한다.
  클라이언트 ID·예외 메시지·cause·SQL·내부 경로·임의 ProblemDetail properties는 출력하지 않는다.
  정제 로그에는 코드·traceId·제한된 원인 타입과 코드 위치만 남긴다. 메시지·바인딩 값은 읽지 않는다.
- 응답이 이미 전송되면 상태를 되돌릴 수 없다. 두 번째 오류 JSON을 덧붙이지 않고 정제 로그만 기록한다.
  컨테이너 parser·프록시가 앱 경계 밖에서 만든 오류는 실제 진입점 통합 검증이 필요하다.
- 일반 요청의 body는 1 MiB, 헤더는 16 KiB, JSON 깊이는 32다. 중복 JSON key·미등록 필드는 거부한다.
  프록시의 크기·압축·framing·속도 제한은 배포 단계에서 함께 검사한다.
- Security는 `/error` 정규화와 ERROR dispatch 외에는 기본 거부한다. 자동 생성 사용자, HTTP Basic,
  form login, logout, 인증·CSRF 발급 endpoint를 만들지 않았다. CSRF는 HttpSession 저장소를 사용한다.
- Session은 Redis indexed repository, 30분 유휴·8시간 절대 수명, 버전 있는 환경별 namespace,
  HttpOnly/Lax/Path=/의 브라우저 Session 쿠키다. local만 `local-session`·Secure=false이고 나머지는
  `__Host-session`·Secure=true다. Domain·URL rewriting은 허용하지 않는다.
- Session JSON은 HTTP mapper와 분리한다. Security 공식 Jackson 3 모듈의 허용목록과 명시한
  메타데이터 스칼라 타입만 역직렬화한다. 최상위 Session 속성은 `String/Long/Integer/Boolean`,
  `SecurityContextImpl`, `DefaultCsrfToken`, indexed 생성 이벤트의 `HashMap`을 허용한다.
  중첩 값도 Security 타입 검증기를 통과한다. 임의 principal·IdP 객체는 추가 검증 전 거부한다.
  현재 인증은 없으며 사용자 모델·권한 철회·동시 세션 상한·신구 배포 호환은 후속 인증 단계다.
- MyBatis 자동 설정과 `JdbcTransactionManager`는 단일 DataSource를 공유한다. SIMPLE,
  2차 캐시 없음, STATEMENT local cache를 사용한다. 업무 Mapper·SQL·schema·seed는 없다.
  업무의 서비스 메서드에 필요한 트랜잭션을 명시하며 직접 SqlSession/connection 경로를 만들지 않는다.
- Flyway 자동 적용은 local만 허용한다. 공유 환경의 전용 migration 경계는 위 절차를 따른다.
- OpenAPI와 Swagger UI는 기본 비공개다. 실제 API가 정해지면 승인된 격리 export 경로에서
  구체 DTO 명세 → Orval 생성 → 타입 검사를 연결한다.

## 검증 상태와 인계

2026-10-26의 과거 검증에서는 Microsoft Build of OpenJDK 25.0.5.0.2로 15개 격리 테스트와
패키징이 통과했고, 이후 Windows x64 Eclipse Temurin 25.0.4.1+1 LTS HotSpot으로 전환한 뒤에도
같은 테스트와 JAR 재생성이 통과했다. 당시 사용했던 라이브러리별 체크섬 검증은 사용자 결정에 따라
현재 기본 정책에서 제거했다.

체크섬 관리 단순화 직후에는 Temurin 25.0.4.1+1과 Java 25·ADOPTIUM toolchain으로 검증 메타데이터나
검증 override 옵션 없이 `test bootJar --offline`을 포함한 검사를 실행했다.
15개 테스트가 실패 0·오류 0·건너뜀 0으로 통과했고 JAR도 실제 재생성했다.
비공개 임시 init script를 이용한 IDE 소스 해석도 앱/테스트 130건·플러그인 23건,
총 153건이 누락 없이 통과했다. 이 검사 task는 기본 빌드에 추가하지 않았다.
실제 IntelliJ Gradle reload와 소스 열기는 사용자가 수행해야 한다.

로컬 연계 설정 이후에는 같은 Temurin JDK로 `test bootJar --offline`을 실행하여
17개 격리 테스트가 실패 0·오류 0·건너뜀 0으로 통과했다. 폐기된 로컬 ACL 파일을 읽던 테스트는
제거하고, 네 프로파일의 local 기본값 격리·환경변수 override·공유 필수값 누락 및 공개 자격 거부를
검사했다. 기존 오류 비노출·CSRF·쿠키·직렬화 회귀도 유지한다.

실제 연계 대상은 준비된 `starter-project`의 PostgreSQL 18.6 / Redis 8.10.2뿐이다.
2026-10-26에 local 기본 접속값으로 빌드한 JAR을 127.0.0.1:8080에 기동하여 다음을 확인했다.

- DB 읽기 전용 연결 유효성 및 Redis indexed probe Session 저장·조회·삭제 성공.
- Redis 생성 이벤트 PSUBSCRIBE 패턴 1개, DB 0의 del/expired 채널 각각 구독자 1개.
- Flyway 12.4.0의 migration 0개 검증·기동 성공. 최초에 `public.flyway_schema_history`만
  생성됐고 이력 행은 0개였다. 업무 테이블·SQL·seed는 추가하거나 실행하지 않았다.
- 익명 GET은 401 `UNAUTHENTICATED`, 직접 `/error`는 안전한 500 `INTERNAL_ERROR`.
  HTML Accept·노출 유도 query에서도 같은 허용 필드와 traceId를 사용했고 CSP·nosniff·no-store를
  확인했다. HEAD는 본문이 없었다. 미정의 경로도 기본 거부 401이며 공개 업무 API는 없다.
- `bootRunLocal --offline --dry-run`에서 일반 `bootRun` 실행 없이 local task 그래프를 확인했다.
  실제 기동 검사는 JAR로 수행했으며 IntelliJ UI 실행·프론트 프록시는 아직 검사하지 않았다.

검사 후 생성한 Java 프로세스만 종료했다. Redis 리스너 패턴은 다시 0개였으며 기존 컨테이너와
volume은 유지했다. probe의 잔여 만료/index 키는 repository의 TTL·정리 정책을 따르며 임의 삭제하지
않는다. `local-session` 쿠키 이름 변경을 위해 기존 세션 키를 삭제하지 않았다.
JDK는 명령 프로세스에만 적용했고 개인 JDK 경로를 공개 설정에 넣지 않았다.

최종 실행 증거는 작업 채팅에서도 보고한다. DB01~DB07, AF01~AF08, 실제 HTTPS·프록시,
Docker 없는 외부 서비스 이식·세션 복원/장애·실제 로그인/회원 조회는 아직 통과하지 않았다.
전체 ASVS 준수·실제 인증·운영 배포 준비 완료를 의미하지 않는다.

로컬 서비스 준비·역할·복구 절차는 [infra/local](../infra/local/README.md)을 확인한다.
