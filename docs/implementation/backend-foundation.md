# 백엔드 공통 기반 최종 설계

기준일: 2026-10-30 / 버전: 1.1 / 상태: 공통 기반 구현·로컬 연동·독립 리뷰 완료. 이후 로그·공격 대응·파일 기능의 마감 상태는 [현재 현황](foundation-status.md)과 [추가 설계](attack-defense-and-files.md)를 따른다.

## 0. 적용 범위와 읽는 순서

이 문서는 1차 프로젝트 세팅 이후의 백엔드 공통 기반을 설계한다. 1차 구현·검증 기록을 이번 기능의 완료 증거로 사용하지 않는다. 설계만 작성하며 작업자 전달, 코드 변경, DB 실행, 커밋·push를 수행하지 않는다.

| 구분 | 계약 소유 문서 |
| --- | --- |
| 1단계 공통 구조·설정·날짜·메시지 | 이 문서, [공통 API](api-contract.md), [데이터 접근](persistence.md) |
| 2단계 로그·접근이력 | [로그와 감사 설계](backend-observability.md) |
| 3단계 인증·권한·토큰·확장 | [인증 설계](authentication-foundation.md) |
| DB·Flyway·선택 테이블 | [데이터 모델](backend-data-model.md) |
| 4단계 문서·프론트 계약·검증·작업 범위 | [인계와 검증](backend-handoff.md) |

2026-10-29 확정 사항은 기존 문서의 API 세션 인증·동시 세션 3개·단계별 로그인 지연·일괄 DEBUG 금지 기본안을 대체한다. 범용 docs/common-rules는 변경하지 않는다. 보안 문서의 나머지 통제는 계속 적용하며 이전 검증 ID는 새로운 동작에 연결해 재검증한다.

### 포함과 제외

- 포함: 로그/SQL·필터·요청 문맥·오류/메시지·검증·사용자/부서/역할/권한·자체 로그인·JWT/갱신·Redis/JDBC·공유 세션 공존·접근/보안 이력·최소 조회 예제·문서/검증.
- 기본 인증 상태 저장소는 redis. jdbc도 동등 동작을 제공한다. memory는 제공하지 않는다.
- 제외: 실제 SAML/OIDC/agent 제품 연동, 가입/탈퇴/메일 복구/MFA, 관리 화면 전체, 파일/엑셀·메일·알림·분산락 제품·일반 캐시·스케줄러 플랫폼·모니터링·CI/배포 구성.
- 만료 정리는 해당 저장소의 필수 유지 기능으로만 구현하며 별도 배치 제품을 설치하지 않는다.
- 프론트 상세 UI는 후속. 연동 계약과 정밀 숫자 예제 요구는 지금 고정하되 화면을 임의 구현시키지 않는다.

## 1. 구조와 책임 (1-1)

기존 기본 패키지 com.example.starter를 유지한다. common/{config,web,error,message,persistence}, auth/{config,web,service,model,store}, user/{web,service,mapper,model}, department, permission, audit로 기능을 나눈다. 기존 공통 구현의 역할에 맞춰 이동·재사용하고 동일 기능을 이중 구현하지 않는다.

- Controller: 공개 DTO 검증·서비스 호출·ApiResponses 반환. Mapper 직접 호출 금지.
- Service: 업무 판단·원자적 변경·권한 및 데이터 범위. HTTP ResponseEntity를 반환하지 않음.
- Mapper: SQL 실행·명시한 결과 매핑. Spring 관리 SqlSession 사용.
- model: 요청/응답/조회 모델 구분. 모델 수를 맞추기 위한 일대일 복제·BaseEntity 일괄 상속·Service/Impl 일괄 생성 금지.
- 인터페이스는 실제 교체 경계인 AuthStateStore(redis/jdbc)에 둔다. 인증 제품별 표준 Spring 확장점을 우선한다.
- Lombok은 생성자/필요 접근자에 제한. 비밀번호/토큰 모델의 toString·무차별 setter·@Data 금지.

## 2. 환경·설정 (1-2)

application.yml + application-local/dev/stg/prod.yml, 정확히 한 프로파일을 명시한다. @ConfigurationProperties + Bean Validation으로 타입·조건부 필수값을 시작 시 검증한다. 정책 수치는 아래 표가 단일 기본값이며 구현자가 임의 변경하지 않는다.

| 설정 키 | 기본값 / 조건 |
| --- | --- |
| app.auth.state-store | redis; redis 또는 jdbc만 |
| app.auth.jwt.issuer / audience | local은 urn:starter:local / starter-api-local, 공유 환경은 외부 필수 |
| app.auth.jwt.active-key-id / private-key-location / public-keys-location | 공유 환경 필수, 신뢰한 파일/Secret 공급 경로. 토큰 헤더의 URL로 키 조회 금지 |
| app.auth.jwt.clock-skew | 30s, 허용 0~60s |
| app.auth.access-ttl | 10m |
| app.auth.idle-timeout / absolute-timeout | 30m / 8h, 양수 및 절대 >= 유휴 |
| app.auth.concurrent-login | allow; allow / deny-new / replace-old |
| app.auth.failed-login.window / max-attempts / lock-duration | 15m / 5 / 15m |
| app.auth.failed-login.ip-max-attempts / ip-window | 30 / 1m. 공용망 정책에 따라 조정 |
| app.auth.namespace | starter:{환경}:auth. 전체 서버 동일, 다른 앱/환경 분리 |
| app.auth.prelogin-session.enabled / timeout | false / 10m. 외부 인증 활성 시 true 필수 |
| app.auth.prelogin-session.namespace | starter:{환경}:prelogin. 토큰 상태와 분리 |
| app.auth.csrf.max-age | 8h, 로그인/로그아웃 시 교체 |
| app.logging.file-enabled / directory | local false, 나머지 true / ./logs |
| app.logging.max-file-size / max-history | 100MB / 30일 |
| app.logging.application-total-cap / error-total-cap | 3GB / 1GB (각 압축 보관분) |
| app.logging.sql.enabled / slow-threshold / max-chars | local true, 나머지 false / 1s / 16000 |
| app.audit.retention-days | local 30; 공유 환경은 외부 필수 양수, 법정 보관 기간 주장 아님 |
| app.audit.query.max-range-days | 31 |
| app.bootstrap.local-data-enabled | local true, 그 외 false 고정 |

이 키는 프로젝트 설정이며 Spring 내장 키가 아니다. 환경변수 매핑명은 표준 relaxed binding의 APP_AUTH_STATE_STORE 등으로 README에 남긴다. DB/Redis TLS·쿠키·오류 비노출 설정은 기존 configuration 계약 유지. JDBC 모드는 Redis bean/접속/키공간 listener/readiness를 만들지 않으며 Redis URL이 없어도 시작한다. prelogin=false면 Spring Session repository/filter 자체를 비활성화한다.

로그 레벨은 root INFO, 우리 패키지 기본 INFO. local SQL 전용 logger만 DEBUG를 허용한다. local은 우리 패키지 DEBUG/TRACE 선택 가능, 공유 환경은 기본 비활성. 프로파일 이름을 업무 코드 곳곳에서 비교하지 않는다.

local JWT 키는 무설정 IDE 실행 편의를 위해 DB에 최초 1회 생성한 개발 전용 키를 공유한다(데이터 모델 참조). 실제 개인키를 소스·문서에 넣지 않는다. 공유 환경은 이 경로를 금지한다. 기본 키·예제 계정으로 운영 가능하다고 안내하지 않는다.

## 3. 공통 API·오류·메시지 (1-3~1-6)

[api-contract.md](api-contract.md)가 envelope/Problem Details를 소유한다. 성공 data/traceId, 오류 type/title/status/code/traceId와 선택 fieldErrors만 반환한다. detail/message/instance/path/rejectedValue/stack/SQL·원문 cause는 금지. 204/HEAD는 본문 없음. ApiResponses.ok/created/noContent를 재사용한다.

- 기능별 ErrorCode(enum)는 공개 code·HttpStatus·서버 메시지 key 연결. BusinessException은 코드와 안전한 문맥만 갖고 cause는 내부 보존.
- 서버: resources/messages/errors_ko.properties, ValidationMessages_ko.properties. 프레임워크 getMessage/검증 기본 문구를 API로 전달하지 않는다.
- 프론트: code/field를 한국어 메시지 모듈에 매핑. 미등록 코드는 일반 안내+traceId 문의 정보. 동일 오류를 필드와 toast에 중복 표시하지 않음.
- 외부 공개 메시지 params 필드를 추가하지 않는다. 길이·범위 문구는 API별 명세의 상수와 일치. 메시지 키 누락·fallback 검증.
- @NotBlank/@Size/@Email 등 입력 검증은 DTO, 중복/상태/데이터 접근은 Service, 최종 무결성은 DB UNIQUE/FK/CHECK.
- 알 수 없는 요청 필드·잘못된 enum·숫자·날짜는 400. 공백 trim/HTML 제거/비밀번호 정규화 일괄 적용 금지.
- 일반 본문 1MiB, 헤더 16KiB, JSON 깊이 32의 기존 상한 유지. 폼 외 파일/stream 별도 계약 필요.

## 4. 날짜·숫자·JSON (1-7)

| 용도 | 공개 문자열 / 내부 타입 |
| --- | --- |
| 날짜 | uuuu-MM-dd (표시 설명 yyyy-MM-dd) / LocalDate / PostgreSQL date |
| 초까지 시각 | uuuu-MM-dd HH:mm:ss / UTC 시점 |
| 밀리초 시각·로그·생성/수정 | uuuu-MM-dd HH:mm:ss.SSS / Instant / timestamptz(3) |

예: 2026-10-29 14:30:00.123. 문자열에 Z나 offset을 붙이지 않으며 이 API의 시각 문자열은 UTC라는 계약을 명시한다. 파서는 STRICT, 유효 연도 0001~9999, 없는 날짜 거부. 시점 생성은 주입 가능한 Clock.systemUTC(), 밀리초 미만은 저장 전 절삭해서 API/로그/DB 반올림 차이를 없앤다. DB connection timezone UTC, JSON과 로그 formatter도 UTC. 초 단위 모델은 명시한 API에만 쓰고 시점 기본은 밀리초다.

DB는 날짜를 문자열로 저장하지 않는다. DB 도구의 임의 화면 표시 포맷까지 애플리케이션이 강제하지 않으며 조회용 export/to_char에서 같은 형식을 적용한다. JWT iat/nbf/exp(NumericDate), HTTP Date·Expires·Retry-After, Spring Session 프레임워크 epoch 컬럼은 해당 프로토콜 형식을 유지한다. 숫자 epoch까지 날짜 문자열로 변경해 표준을 깨지 않는다.

- JSON camelCase. 빈 목록 [], 값 없는 일반 필드는 null 유지, fieldErrors 같은 선택 계약은 생략 가능.
- bigint 식별자는 공개 DTO에서 숫자 문자열. Java Long의 전역 serializer를 바꿔 page까지 문자열로 만들지 않는다.
- page/size/totalPages/totalElements는 기존 number 계약 유지하되 JS 안전 정수 범위 안에서 보장하고 넘으면 명시 오류/별도 계약. 큰 업무 정수는 문자열.
- 금액·정밀 수량은 BigDecimal와 NUMERIC(p,s), API decimal 문자열(지수 표현 금지). double/float 금지. 반올림은 업무별 확정, 이번 계산 예제는 HALF_UP·소수 2자리, 입력 초과 소수는 자동 절삭 대신 검증.
- 프론트 후속 필수 예제: 0.1+0.2, 단가×수량·합계·음수·반올림·큰 ID·문자열 API 왕복. decimal 연산 도구는 프론트 설계 때 선택; 지금 임의 패키지 추가 금지. Number()/parseFloat()로 정확도 상실시키지 않음. UTC 문자열을 브라우저 new Date(text)에 그대로 넘기지 않고 명시적 파서/표시 시간대 사용.

## 5. 목록·코드·데이터 (1-8~1-10)

- page 0 기반, size 기본20/최대100, 잘못된 범위 400. 조회·count는 동일 권한/검색 조건, 안정 정렬 마지막에 PK. 페이지 offset 계산 overflow 검사.
- sort는 기능별 enum/매핑 표에서 SQL 상수로 변환. 클라이언트 문자열 ${sort} 직접 삽입 금지. 기본 예제는 q 0~100자와 명시 sort 하나, 와일드카드 검색 특수문자 escape 규칙 고정.
- 배포와 함께 바뀌는 상태/행위/권한 코드 enum, 관리자가 편집하는 코드·부서·역할 DB. 범용 공통 코드 CRUD 화면은 이번 범위 아님.
- MyBatis mapUnderscoreToCamelCase=true, SIMPLE, localCacheScope=STATEMENT, cacheEnabled=false. 같은 DataSource/JdbcTransactionManager, @Transactional 업무 단위·rollbackFor=Exception.class. 자동 메서드명 트랜잭션 금지.
- 테이블/컬럼은 실제 대문자 언더바, PostgreSQL에서 큰따옴표 사용. 단순 column↔camel 프로퍼티는 resultMap 없이 자동 매핑, 충돌 조인은 명시 별칭, 중첩/생성자 복합 매핑에만 resultMap 허용.
- 변경 가능한 기준 모델에 CREATED_AT/BY·UPDATED_AT/BY, 필요 VERSION. 시스템 변경은 BY=null 및 별도 행위자 구분, 가짜 사용자 0 금지. MyBatis에는 JPA auditing이 자동 적용되지 않으므로 서비스의 공통 Actor/Clock과 SQL로 지정.
- 임시 테이블은 쓰기 업무 트랜잭션·동일 물리 연결·ON COMMIT DROP, pool 재사용/롤백 후 잔여 검증. 세부는 persistence.md.

## 6. 설계 완료와 실행 경계

1~4단계 사용자 정책은 해소했다. 아래는 배포 시에만 공급할 값이며 기반 구현 착수를 막는 미결정은 아니다: 실제 도메인/프록시/TLS, 운영 키 공급·MFA, 외부 IdP/agent 계약, 접근이력 법적 보존기간·운영 담당자, HA/백업/RPO.

최종 문서의 상세 기본값·테이블명·API payload·알고리즘 선택은 사용자 확정 정책을 구체화한 설계자 결정이다. 실제 외부 서비스·데이터 변경·migration·seed·쓰기 테스트·컨테이너 조작은 별도 승인 범위를 확인한 뒤 실행한다. 문서 완성만으로 이를 실행하지 않는다.

## 7. 이번 문서 점검 결과

2026-10-29: 변경 문서·진입 지침 26개(신규 설계4개 포함)의 상대 링크186개를 검사해 누락0개, tracked diff 공백 오류0개를 확인했다. 새 문서의 개인 절대 경로·조정 기록·실제 개인키 표식 검사는 발견0개였다. JWT/세션 분리·동시 로그인·로그인 제한·CSRF·권한 코드·쿠키 수명·다중 탭 갱신 흐름을 수동 대조하고 수정했다. 독립 코드리뷰·코드 빌드·기능 테스트·DB/Redis/컨테이너 실행·migration·seed·커밋·push·작업자 전달은 이번에 수행하지 않았다.
