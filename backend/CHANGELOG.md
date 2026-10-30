# 백엔드 변경 이력

최신 변경부터 정리했습니다. 아래 “미실행”은 해당 작업을 마쳤을 당시의 상태이며, 현재 확인 결과는 VERIFICATION.md를 참고하세요.


## 2026-10-30 — 실제 파일 연동과 최종 보안 보완

- LIVE-CLOSE-001(P1): 쿠키에서 bearer를 찾으면 Resource Server 기본 구성이 CSRF를 제외하던 문제를 실제 연동에서 발견했습니다. 완성된 CsrfFilter에 기본 검사 matcher를 복구해 유효/만료/잘못된 access 문자열과 무관하게 모든 변경 요청을 검사합니다. 단순 matcher 설정만으로 자동 제외를 상쇄했다고 판단하지 않습니다.
- CLR-LOCAL-001: local 파일 기본 경로를 `./.private/files`로 옮겨 Gradle clean과 분리했습니다. 기존 Git 제외 규칙을 재사용하며 기존 `build/files`를 자동 이동·삭제하지 않습니다. 명시한 외부 검증 루트에는 영향이 없습니다.
- 전체96개 시험 중95개 통과·Windows 링크 권한 제외1개, JAR 빌드 성공입니다. OpenAPI16 paths/18 operations/32 schemas와 기존 migration5개는 동일합니다.
- 승인된 전용 JDBC DB의 core V2 적용/재실행과 A/B 실제 PNG/JPEG·한글 이름·소유권·HEAD·CSRF·동시 용량·처리 제한을 확인했습니다. 전용 fixture의 실제 SQL rollback·저장 실패 보상·삭제 실패 재시도도 통과했습니다.
- manifest28개 파일의 CREATE/DELETE 감사와 제거를 대조했고 rollback2개는 행/성공 감사가 없습니다. 다른 검수에서 만든 manifest 밖 파일은 보존했습니다. 기존 기본 서버·DB·Redis·컨테이너는 유지했습니다. 자세한 한계는 VERIFICATION의 SECFILE-EXEC-001 절을 따릅니다.

## 2026-10-30 — 요청 공격 경계와 이미지 파일 공통 기능

- CLR1-001: HEAD 본문을 없애면서 다운로드 스트림을 닫지 않던 문제를 수정했습니다. 실제 컨트롤러와 본문 advice를 함께 등록해 반복 HEAD의 빈 본문/핸들 해제/권한/감사와 GET 전송을 회귀 검증했습니다. 전체93개 중92개 통과·권한 제외1개, JAR 성공, OpenAPI는 동일합니다.
- 기본 StrictHttpFirewall과 사용하지 않는 헤더 검사, 단일 query 중복 거부를 연결했습니다. 기존 JSON 중복/미등록 필드·바인딩 SQL·정렬 허용 목록을 회귀 검증했습니다.
- PNG/JPEG 실제 업로드·본인 목록·attachment 다운로드·삭제와 FileStore/전용 로컬 디스크 구현을 추가했습니다. 소유자는 서버 인증 주체로만 결정합니다.
- 디코딩 전 크기·픽셀 검사와 메타데이터 없는 재인코딩, 파일/요청/소유자 용량·업로드 횟수·처리 동시성 제한을 적용했습니다.
- 새 core V2에 파일 메타데이터와 용량 테이블을 작성하고 PENDING/READY/DELETING과 commit 결과에 따른 실패 복구를 구현했습니다. 기존 적용 migration은 보존했습니다.
- 실제 Controller에서 OpenAPI16 paths/18 operations/32 schemas를 생성했습니다. 새 의존성은 없습니다.
- DB 없는 테스트·임시 디스크·임시 Tomcat 파서 검증을 수행했습니다. 새 migration 적용·실제 제품 파일 저장소 기동은 별도 실행 계획 대상으로 남겼습니다. 기존 서버·DB·Redis0는 유지했습니다.
- CLR1 수정 전 자체 검수에서 migration CLI의 필수 환경변수와 Windows 실행 예시, APP_FILES_ROOT·포트·Redis DB 선택, 상대 저장 경로의 기준을 보완했습니다. 당시 기본 시험은91개 중90개 통과, 심볼릭 링크 권한으로1개 제외이며 실제 파일 SQL/디스크 장애 검증은 미실행입니다.

## 2026-10-29 — 로컬 콘솔과 SQL 파라미터 보기 개선

- Boot 배너와 START/READY/STOP을 다시 보여줍니다. READY는 모든 준비가 성공한 뒤 실제 포트와 함께 한 번 나옵니다.
- 색상은 콘솔 지원 여부와 옵션을 따릅니다. 색상을 꺼도 로그 분류를 구분할 수 있고, 빈 요청 문맥은 반복하지 않습니다.
- DB·HTTP 시작/연결 메시지와 안전한 오류 요약을 읽기 쉽게 정리했습니다. 원인 종류·코드 위치는 여러 줄로 보여주며 비밀과 원문 SQL 예외는 숨깁니다.
- 실제 BoundSql과 바인딩 순서의 SQL/PARAMS를 표시합니다. 안전한 숫자·ID·코드·날짜만 보여주고 검색어·개인정보·인증값은 가립니다. 실제 실행 SQL과 타입 핸들러는 바꾸지 않습니다.
- README와 검증 안내를 사용 방법부터 자연스럽게 정리했습니다. 기본67개 시험과 별도 실제 인증4개, 색상 on/off·실패·읽기 전용 연결 검사를 통과했습니다. 공유 로그 설정과 기존 사용자 서버는 유지했습니다.
- 자세한 확인 결과와 한계는 VERIFICATION.md의 로컬 로그 개선 절을 참고하세요. 새 migration/DB 초기화·비밀번호 변경·컨테이너 조작·커밋/push는 하지 않았습니다.

## 2026-10-29 — 승인된 실제 연동과 기동 결함 수정

- 실제 기동의 migration 설정/Bean 이름 충돌, 반복 JWT 인증의 CSRF 쿠키 삭제, JDBC Session 자동설정 제외 클래스 오타를 수정하고 회귀3개를 추가했다.
- 병렬 refresh 시험은 재사용 폐기가 CAS 승자의 후속 발급보다 빠를 수 있는 안전한401 경쟁도 정확히 검사한다.
- 신규PG3DB migration 각2회·개발키/A·B·Redis3정책·JDBC·Session on/off·실제 감사장애/rollback/TEMP/정밀타입·권한회수복원을 검증했다. 단위57개와 실제HTTP16개 통과, 공개OpenAPI는 동일하다.
- 기존 DB/Redis0·컨테이너/volume을 보존했고 신규 자료를 초기화/삭제하지 않았다. 상세 한계와 실행 상태는 VERIFICATION의 FOUNDATION-EXEC-001 절을 따른다.

## 2026-10-29 — BACK-FOUNDATION-R1 회귀 수정

- BFR1-001: 검증된 최초 독립 history만 명시적 baseline0으로 초기화해 V1/localV2 누락 없이 실행한다. 자동 baseline은 금지하고 기존 이력·소유 객체 충돌은 거부한다.
- BFR1-002: 로그인 응답 projection을 새 sid 부분 발급 실패의 폐기 경계에 포함한다.
- BFR1-003: 정규화된 직접 peer를 양 감사 CLIENT_IP에 영속하고 SYSTEM은 NULL로 유지한다. 공개 DTO/진단에는 원문 IP를 추가하지 않는다.
- DB 없는 회귀6개와 전체54개·JAR 빌드를 확인했다. OpenAPI 해시는 동일하며 실제 저장소 실행은 미검증이다.

## 2026-10-29 — JWT·공유 인증·감사 공통 기반

- common 패키지로 기존 경계를 이동하고 typed 정책·UTC/정밀 숫자·메시지·목록 검증을 추가했다.
- JWT cookie/CSRF·Origin·Argon2, Redis Lua/JDBC guard 상태와 rotation·reuse·동시 정책·fence,
  선택 prelogin Session을 구현했다. 키/상태 복구 세대와 감사 HMAC secret 공급을 구체화했다.
- 사용자/부서/권한·감사 Service/Mapper, core/선택/local 독립 migration과 local 키 초기화 코드를 작성했다.
- 로그/SQL/요청 경계·필수/일반 감사·구체 Controller/OpenAPI 및 격리/선택 live 시험을 추가했다.
- 실제 DB/Redis 쓰기·migration/seed/서명 키 생성·제품 앱 기동·두 서버 시험은 실행하지 않았다.
  실제 명령/결과·ID별 부분 검증·미실행 및 승인 계획은 [VERIFICATION.md](VERIFICATION.md)를 따른다.
- 현재 사용 방법은 [README.md](README.md)를 참고하세요. 아래 1차 기록은 당시 세션 기반 구현의 이력입니다.

## 2026-10-26 — 1차 API·로컬 연동 기반

### 변경과 현재 선택

- Java 기본 패키지 `com.example.starter`, Eclipse Temurin 25 LTS HotSpot,
  Spring Boot 4.1.2, Gradle Wrapper 9.8.2로 API 모듈을 구성했다.
- MVC·Security·Bean Validation·Lombok·MyBatis/JDBC·PostgreSQL·Flyway·Redis Session·
  springdoc과 기본 로깅을 연결했다. 버전은 BOM·lockfile, Gradle 배포본은 Wrapper SHA-256으로
  고정한다. 라이브러리별 수동 체크섬 등록은 기본 관리에서 제외한다.
- 성공·페이지 모델과 반환 편의 함수, 고정 오류 계약, 서버 traceId, 필터·MVC·Security·
  `/error`·ProblemDetail 오류 비노출 경계를 마련했다. 기본 거부·CSRF·세션 JSON 허용 타입·
  쿠키·세션 수명과 요청 크기·JSON 입력 제한을 적용했다.
- local은 준비된 `starter-project`의 루프백 DB·Redis에 공개 가짜 자격 `testuser / 1234`로
  기본 연결한다. `bootRunLocal`과 IntelliJ 실행 안내를 제공하며 쿠키명은 `local-session`이다.
  일반 실행은 프로파일을 명시해야 한다. dev/stg/prod는 외부 필수값·TLS·보안 쿠키를 유지한다.
- Flyway 자동 적용은 local만 허용한다. 업무 SQL은 후속 설계 후 backend의 버전 migration으로
  관리하며 현재 업무 테이블·seed·가짜 migration은 없다. 공유 환경은 자동 실행을 끄고
  전용 migration 자격·배포 절차를 사용한다.
- 루트 제외 규칙·문자 인코딩·줄바꿈·Java 4칸/JS·TS 2칸 및 100자 형식 기준을 정리했다.

### 실제 검증

- Temurin 25.0.4.1+1에서 `test bootJar --offline`: 17개 격리 테스트 통과,
  실패·오류·건너뜀 0, JAR 생성 성공. 환경 격리·오류 비노출·CSRF·쿠키·직렬화를 확인했다.
- 준비된 로컬 PostgreSQL 18.6의 읽기 연결과 Redis 8.10.2 indexed Session probe의
  저장·조회·삭제, 생성·삭제·만료 이벤트 구독을 확인했다.
- local JAR 기동 시 Flyway 12.4.0이 migration 0개로 시작하고 이력 테이블만 생성했다.
  이력 행은 0개였으며 업무 데이터 변경은 없었다.
- 실제 HTTP에서 익명 401·안전한 `/error` 500·HEAD 빈 본문과 trace·CSP·nosniff·no-store를
  확인했다. 검사 후 생성한 Java 프로세스만 종료하고 기존 컨테이너·volume을 유지했다.
- `bootRunLocal` 실행 그래프의 dry-run과 형식 검사를 확인했다. 이전 단계의 Microsoft JDK
  검증 및 메타데이터 없는 소스 해석 결과는 README에서 현재 검증과 구분한다.

### 미검증·후속 범위

- IntelliJ UI 실행·Gradle reload·소스 열기, 프론트 프록시, 실제 HTTPS 진입점.
- 실제 로그인·회원 조회·다중 서버 인증·세션 복원/장애·외부 서비스 이식 및 업무 SQL 시험.
- 개별 업무·인증 endpoint와 schema는 미구현이다. 전체 ASVS 준수나 운영 배포 준비 완료를
  의미하지 않는다. 상세 실행·검증 경계는 [README](README.md)를 따른다.
