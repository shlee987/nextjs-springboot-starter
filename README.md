# nextjs-springboot-starter

Next.js·React 프론트와 Spring Boot·MyBatis 백엔드를 재사용할 수 있도록 만든 기본 프로젝트입니다. Docker는 PostgreSQL·Redis만 실행하고, 프론트와 백엔드는 IDE 또는 터미널에서 실행합니다.

## 로컬에서 실행하기

1. [DB·Redis 준비](infra/local/README.md)에 따라 Docker Desktop에서 `starter-project`를 실행합니다. 로컬 예제 자격은 공개 테스트값 `testuser / 1234`입니다. 데이터는 named volume에 보존됩니다.
2. Java 25를 준비하고 [백엔드 실행 안내](backend/README.md)의 `bootRunLocal` 절차로 실행합니다. local의 첫 실행에는 Flyway·예제 사용자·개발용 키 준비가 포함됩니다.
3. [프론트 실행 안내](frontend/README.md)에 따라 `frontend`에서 `npm install`, `npm run local`을 실행합니다.
4. `/login`에서 local 예제 계정으로 로그인합니다. 일반 사용자는 `localuser`, 관리자는 `localadmin`이며 초기 예제 비밀번호는 `LocalExample1234!`입니다. 공유 환경에는 이 예제 계정을 적용하지 않습니다.

브라우저는 `/api`로 요청합니다. local은 설정한 백엔드로 전달하고, dev/stg/prod는 같은 도메인의 ingress에서 연결합니다. `dev`는 백엔드와 같은 공유 개발 환경을 뜻합니다. 프론트 환경별 빌드는 `build:local/dev/stg/prod`, 빌드 결과 실행은 공통 `npm start`를 사용합니다.

## 구현된 기본 기능

- 자체 로그인, JWT·갱신, Redis 기본/JDBC 선택 인증 상태, 동시 로그인 정책, 권한과 로그아웃·권한 변경 시 인증 회수
- 공통 API·페이지·오류 응답, 입력 검증, UTC 시각·정밀 숫자 처리, MyBatis·Flyway
- 접근/보안 이력, 정제된 오류 진단, local 시작/준비 상태·읽기 쉬운 SQL/PARAMS 로그
- PC·모바일 공통 레이아웃, 폼·선택·달력·알림·그리드·차트·예제, 작업별 중복 실행 방지
- 공격 입력 경계 보완과 PNG/JPEG 파일 업로드·본인 목록·다운로드·삭제. 실제 연결과 독립 리뷰를 마쳤으며 확인 범위·수동 잔여는 아래 최신 기록에서 확인합니다.

예제 메뉴는 local에서 확인합니다. 주요 화면은 `/me`, `/users`, `/departments`, `/audits/access`, `/audits/security`이며, `/examples`에서 컨트롤과 기능을 살펴볼 수 있습니다. 파일 예제는 `/examples/files`입니다.

## 현재 확인 범위

[현재 구현·검증 현황](docs/implementation/foundation-status.md)에 공통 기반의 실제 연동과 후속 작업 상태를 구분했습니다. 과거 1차 설치·기초 연결 결과는 [변경 이력](docs/implementation/change-history.md)으로 보존합니다.

- [백엔드 검증표](backend/VERIFICATION.md): 테스트·실제 DB/인증·파일 처리·로그 확인과 미검증
- [프론트 기능표](frontend/FEATURES.md), [UI 검수](frontend/UI-REVIEW.md), [공통 옵션·중복 실행 방지](frontend/UI-OPTIONS.md), [업로드 검수](frontend/SECFILE.md)
- [실제 OpenAPI](docs/api/openapi.json): 백엔드 Controller에서 생성한 명세입니다. `frontend`에서 `npm run api:gen`으로 클라이언트를 생성합니다.

실제 운영 TLS·프록시·외부 SSO·저장소 공유·복구·보관 정책은 배포 환경에서 별도로 검증해야 합니다. 전체 ASVS/ISMS 준수 또는 모든 취약점 부재를 뜻하지 않습니다.

## 설계와 개발 기준

- [설계 안내](docs/implementation/README.md), [확정 사항](docs/implementation/decisions.md), [기술 버전](docs/implementation/stack-versions.md)
- [백엔드 기반](docs/implementation/backend-foundation.md), [인증·확장·Redis/JDBC](docs/implementation/authentication-foundation.md), [데이터 모델](docs/implementation/backend-data-model.md)
- [API](docs/implementation/api-contract.md), [MyBatis·트랜잭션](docs/implementation/persistence.md), [로그·이력](docs/implementation/backend-observability.md)
- [프론트 기반](docs/implementation/frontend-foundation.md), [UI 정리](docs/implementation/frontend-ui-refinement.md), [공통 컨트롤 보완](docs/implementation/ui-component-review.md)
- [문구·로그 가독성](docs/implementation/readability-and-local-logs.md), [공격 대응·파일 기능](docs/implementation/attack-defense-and-files.md)
- [보안 기준](docs/implementation/security/baseline.md), [검증 기준](docs/implementation/security/verification.md), [공통 개발 규칙](docs/common-rules/AGENTS.md)

공개 저장소에는 실제 비밀·개인정보·개인 PC 경로·검증 로그·업로드 파일을 포함하지 않습니다. 프론트는 npm을 사용하고 잠금 파일을 유지합니다. 수동 체크섬 등록 대장은 사용하지 않으며 패키지 관리자의 integrity·Gradle Wrapper 체크섬과 취약점 대응 기준을 유지합니다.