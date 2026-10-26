# 1차 기반 구성 변경 이력

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
