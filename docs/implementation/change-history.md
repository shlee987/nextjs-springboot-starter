# 프로젝트 기반 구성 변경 이력



## 2026-10-30 — UI·공통 옵션·로그·공격 대응·파일 기능 보완

PC/모바일 UI·글꼴·색/간격·달력과 공통 선택/입력/알림 옵션, 작업별 중복 실행 방지·라디오 선택 표시를 보완했다. local 로그의 시작/준비 상태와 안전한 SQL 파라미터 표시, 쉬운 화면 설명을 정리했다. 요청 공격 경계와 PNG/JPEG 업로드·소유자별 목록·다운로드·삭제를 추가했으며 파일 선택 영역도 별도로 디자인했다. 실제 OpenAPI는16경로·18작업·32schema로 갱신했다. 실제 파일 연결·승인된 실행·미비사항 수정과 독립 재리뷰·최종 문서 점검을 마쳤다. 확인한 범위와 수동/운영 잔여는 [현재 현황](foundation-status.md), 증거는 모듈 검증표를 따른다. 아래 날짜별 기록은 당시 결과로 보존한다.
## 2026-10-29 — 공통 기반 구현·독립 리뷰·승인된 실제 연동 검증

- 백엔드 JWT·Redis/JDBC 인증 상태·선택 prelogin Session·동시 로그인 정책, 사용자/부서/권한·공통 값/오류·메시지·로그/SQL·접근 및 보안 감사와 migration/seed 코드를 구현했다. 프론트 공통 컨트롤·예제 카탈로그·권한 화면·API 연결을 구현했다.
- 두 모듈 독립 리뷰에서 프론트 HEAD401 복구/중첩 Guard, 백엔드 독립 Flyway 이력 초기화/로그인 projection 실패 보상/감사 peer 누락을 수정했다. 재리뷰와 최종 OpenAPI·Orval·CSRF·세대·날짜/숫자·프록시/CSP 코드 경계에서 추가 finding은 없었다.
- 백엔드 외부 서비스 없는 전체54개 시험과 JAR 빌드, 프론트 타입/lint/형식/local·prod 빌드·순수 계산/시간 회귀 및 응답 대역 인증17개 통과를 기록했다. 실제 Controller에서 생성한 OpenAPI13경로·14작업·28schema와 Orval36생성파일이 최종 명세에서 일치한다.
- 위 결과는 실제 저장소 원자성·브라우저 쿠키·다중 탭·다중 서버 시험의 통과를 의미하지 않는다. 승인된 migration/seed/개발키·실서버/브라우저 연동을 수행했고, 실기동에서 발견한 Bean 이름·CSRF 인증전략·Session 제외명과 프론트 진단 ID·캐시 상태 표시를 수정·재리뷰했다. 최종 백엔드57개 독립/16개 실제 HTTP 시험, 프론트 실제 자연 만료 갱신·다중 탭/전체 로그아웃·권한별 조회/캐시 격리 결과와 수동·도구 제한은 [현재 현황](foundation-status.md)에 기록했다. 전체54개 항목 중12개는 일부 검증이 남는다. 요구사항별 부분 검증과 미검증은 [백엔드 검증표](../../backend/VERIFICATION.md), [프론트 기능·검증표](../../frontend/FEATURES.md)를 따른다.
- 실제 외부 SSO provider·운영 배포·전체 ASVS/ISMS 검증은 완료 범위가 아니다. 아래2026-10-26 문구는 당시 구현과 실행 이력으로 보존한다.

## 2026-10-26 — 프로젝트 세팅 마감

프론트·백엔드·로컬 서비스의 재사용 가능한 기본 구성과 공통 계약을 마련했다. 업무 기능과 실제 운영 배포는 후속 단계다. 세부 변경은 [프론트 이력](../../frontend/CHANGELOG.md), [백엔드 이력](../../backend/CHANGELOG.md), [Docker 이력](../../infra/local/CHANGELOG.md)을 참조한다.

### 최종 구성과 계약

- 프론트는 Next App Router·React·TypeScript, npm 단일 package-lock을 사용한다. TanStack Query·Axios·Zustand·React Hook Form·Zod·Orval과 Radix Primitives·AG Grid Community·ECharts 기반을 준비했다. 업무 화면·공통 컨트롤·OpenAPI 생성 결과를 미리 만들지 않았다.
- 개인 PC 실행은 `npm run local`, 환경별 빌드는 `build:local/dev/stg/prod`, 빌드 결과 실행은 공통 `npm start`다. dev는 백엔드와 같은 공유 개발 환경만 의미한다. Next 내부 development 모드와 배포 환경을 구분한다.
- 브라우저 API 경로는 환경 공통 `/api`다. local만 설정한 루프백 백엔드로 전달하고 공유 환경은 ingress가 전달한다. 빌드 입력과 공개 여부를 구분하고 서버 내부 주소·비밀을 브라우저 설정에 복사하지 않는다.
- 백엔드는 Java 25·Spring Boot·MyBatis 기반이다. 성공 envelope·페이지·안전한 오류 응답·traceId, Security 기본 거부·CSRF·공유 Redis Session·직렬화·쿠키·외부 설정 검증을 구성했다. API 응답에 실제 예외 메시지·스택·SQL·내부 경로를 노출하지 않는다.
- local 백엔드는 Docker 테스트 접속 기본값과 `bootRunLocal`을 제공한다. local만 Flyway 자동 적용과 `local-session` 쿠키 예외를 사용한다. dev/stg/prod는 필수 외부 설정·TLS·Secure 쿠키·별도 migration 경계를 유지한다.
- Docker Compose의 `starter-project` 아래 PostgreSQL·Redis를 구성했다. 공개된 가짜 테스트 자격 `testuser / 1234`, 루프백 포트와 데이터 보존 named volume을 사용한다. 프론트·백엔드는 IDE에서 실행한다.
- 라이브러리별 수동 체크섬 등록과 버전별 승인 대장은 기본에서 제외했다. 버전 잠금·패키지 관리자의 자동 integrity·Gradle Wrapper 체크섬은 유지한다. 선택 도구와 추가 공급망 강화는 문서 전용으로 구분한다.
- 공통 재사용 지침은 `docs/common-rules`, 이 프로젝트의 기술 선택·환경·보안 구현 계약은 `docs/implementation`에 둔다. 개인 작업 자료와 긴 검증 로그는 공개 저장소에 포함하지 않는다.

### 확인한 범위

| 영역 | 실제 확인 |
| --- | --- |
| 프론트 | Node 24.22.1·npm 12.2.0 설치 재현, 타입·lint·format·빌드, 55개 설정 입력 검사. local/prod 빌드 각각에서 환경 재주입 없는 시작과 문서/404의 nonce·CSP·no-store 확인 |
| 의존성 | npm 전환 시 직접·전이 패키지 버전 유지, 당시 audit 취약점 0건. 이 결과는 이후 새 취약점이 없다는 보장이 아님 |
| 백엔드 | Temurin 25.0.4.1+1에서 17개 테스트·JAR 패키징 통과. 실제 local JAR 기동, 안전한 401·오류 fallback·HEAD·trace·보안 헤더 확인 |
| DB·Redis | 두 컨테이너 healthy, 인증 접속·잘못된 비밀번호 거부, PostgreSQL 연결, Redis probe 저장·조회·삭제와 indexed Session listener 구독 확인 |
| Flyway | migration 0개 상태로 시작, 이력 테이블만 생성. 업무 테이블·seed 없음 |
| 리뷰 | 독립 코드리뷰·수정·재검토 수행. Docker 단순화 후 삭제 파일을 읽던 백엔드 테스트 참조도 해소 |

### 후속 범위

- 로그인·회원/업무 schema·업무 SQL·실제 SSO/다중 서버 로그인·공통 UI 기능·실제 OpenAPI/Orval 생성은 미구현 또는 미검증이다.
- 프론트 프록시와 실제 업무 API의 종단 연동, 운영 ingress·TLS·외부 서비스 이식·장애 복구·데이터 복원은 이번 기반 검증으로 대체하지 않는다.
- 로그는 SLF4J/Logback·JSON 콘솔·traceId·공통 오류 진단 기반까지만 준비했다. local 콘솔 가독성·환경별 로그 수준·파일 회전 정책은 후속 정리 대상이다.
- 전체 ASVS/ISMS 준수나 운영 배포 완료, 취약점이 전혀 없음을 선언하지 않는다. 실제 기능과 배포 환경을 정할 때 해당 요구사항을 검증한다.
