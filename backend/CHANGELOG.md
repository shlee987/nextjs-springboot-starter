# 백엔드 변경 이력

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
