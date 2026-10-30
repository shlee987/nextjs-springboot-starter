# 1차 프로젝트 세팅 범위와 인계 기준

> 단계 경계(2026-10-29): 이 문서는 완료된 1차 세팅의 범위·이력이다. 후속 백엔드 기능은 [최종 설계](backend-foundation.md)를 적용한다. 여기의 미구현/후속 표현은 1차 당시 상태이며 현재 설계 미정이라는 뜻이 아니다.

기준일: 2026-10-26 / 상태: 세팅 범위·공통 계약 확정, 구현은 별도 프론트·백엔드 세션에서 진행.

기본 구성은 취약점 대응 권고와 안전한 기본 설정에 집중하며 불필요한 수동 관리 대장을 늘리지 않는다. Gradle 의존성별 체크섬 등록과 패키지 버전별 승인·예외 정책은 기본 제외하고, 버전 잠금·자동 integrity·Wrapper checksum은 유지한다. 상세 범위는 [공급망 계약](security/supply-chain.md)을 따른다.

현재 목표는 기능을 완성하는 것이 아니라 이후 기능을 하나씩 설계·구현할 수 있는 공통 바탕을 준비하는 것이다. [확정 버전표](stack-versions.md)와 [공통 API 응답](api-contract.md)을 적용한다. 로그인·업무 API·테이블·공통 컨트롤 상세가 아직 없다는 이유로 1차 세팅 전체를 보류하지 않는다.

## 1. 이번에 포함하는 것과 미루는 것

| 영역 | 1차 세팅에 포함 | 해당 기능 단계로 보류 |
| --- | --- | --- |
| 저장소 | frontend·backend·infra/local·docs 경계, 제외 규칙, 도구 버전·잠금 파일·실행 안내 | 고객 환경별 배포 파일·새 CI 제품 |
| 프론트 | Next/React/TS, 선택한 기본 라이브러리, CSS 기반, Query Provider·공통 요청·오류·RHF/Zod 연결의 최소 기반 | 업무 화면·로그인 화면·세부 공통 컨트롤·차트/그리드 기능 목록 |
| 백엔드 | Boot/MVC, Bean Validation, 공통 성공/오류/페이지 모델·편의 반환 함수, 실제 예외 비노출 처리, 로깅 | 도메인 서비스·업무 DTO·Mapper SQL·세부 API 경로 |
| 데이터 | PostgreSQL driver·MyBatis·트랜잭션 기본 설정, Flyway 실행 경계, Redis 연결 | 회원/업무 schema·seed·프로시저·임시 테이블 예제 |
| 인증 | Spring Security, Redis 공유 HttpSession, CSRF 기반·기본 거부·공통 401/403·안전한 쿠키·직렬화 설정 | 사용자 저장소·비밀번호 검증·인증 endpoint·계정/권한 모델·SSO 실제 연동 |
| API 문서·생성 | springdoc과 Orval 설정, 공통 모델·OpenAPI 경로/전송 규칙 | 업무 API 명세·실제 client Hook 생성 결과 |
| 환경 | local/dev/stg/prod 구분, 외부 설정·검증·예제·로컬 DB/Redis 준비 절차 | 실제 dev/stg/prod 주소·비밀값·배포 인프라 연결 |
| 검증 | 빌드·타입·lint·최소 JUnit/Spring 검사, 설정·응답 기반 검증 | 실제 로그인·다중 서버 인증·업무 SQL·공통 UI 기능의 통합 검증 |

기본 라이브러리는 버전표의 범위만 설치한다. 사용하지 않는 라이브러리를 위해 예제 기능이나 범용 추상 계층을 선구현하지 않는다. Spring Boot 자동 설정·DB 준비가 필요한 경우 명령별 부수 효과와 승인 범위를 기록한다.

## 2. 기본 폴더와 세션별 소유권

| 위치 | 담당·내용 |
| --- | --- |
| frontend/ | 프론트 세션: Next 앱·공통 요청·Query Provider·상태/폼 도구 연결·CSS·프론트 도구 설정 |
| backend/ | 백엔드 세션: Boot·보안·응답·설정·데이터 접근·OpenAPI |
| infra/local/ | 백엔드 세션: 로컬 PostgreSQL·Redis 준비와 자격/ACL·migration 실행 경계 |
| docs/implementation/ | 설계 담당: 양쪽 공통 계약·단계별 상세·검증 기준 |
| 루트 도구·제외 규칙·README | 먼저 작업하는 세션이 초기화하고 다른 세션은 중복 생성하지 않음. 변경 충돌을 확인 |

Java 기본 패키지는 com.example.starter로 정한다. 개인명·회사명·고객 도메인을 기본 소스에 넣지 않는다. 상세 하위 패키지는 공통 규칙에 따라 실제 책임 단위로 작성한다.

한 checkout에서 두 세션이 작업할 때는 담당 파일 경계를 지키고 공통 설정을 동시에 수정하지 않는다. 어느 세션도 다른 세션의 변경을 초기화하거나 전체 파일을 덮어쓰지 않는다. 새 worktree·브랜치·PR의 생성은 실제 개발 세션의 작업 방식에 맞춰 별도 결정한다.

## 3. 환경·프로파일 계약

배포 환경 이름은 local·dev·stg·prod 네 개로 통일한다. 기존 문서에서 staging/production이라고 표현한 것은 각각 stg/prod의 환경 의미다. 테스트 전용 구성이 필요해지면 기술용 test 설정을 분리할 수 있지만 다섯 번째 기본 배포 환경으로 추가하지 않는다.

| 환경 | 목적 | 정책 |
| --- | --- | --- |
| local | 개인 PC 개발 | 루프백 HTTP·HMR 예외, 로컬 PostgreSQL/Redis. 공개 가짜 testuser/1234 기본값 사용 |
| dev | 공유 개발 서버 | 외부 주입 설정·HTTPS·보안 쿠키. local 완화 자동 상속 금지 |
| stg | 운영 전 검증 | prod와 같은 보안·렌더링 기준, 별도 자격·격리 데이터 |
| prod | 운영 | 안전한 필수값 검증, 최소권한, 개발·Swagger·디버그 기능 비활성 |

### 백엔드

- 공통 application.yml과 application-local.yml·application-dev.yml·application-stg.yml·application-prod.yml을 둔다. 파일에는 비밀 없는 기본값·정책만 넣는다.
- SPRING_PROFILES_ACTIVE로 배포 프로파일을 명시한다. 네 배포 프로파일 중 정확히 하나만 활성화하고 누락·상충 조합은 안전하게 실패시킨다. 공통 파일에서 local로 자동 fallback하지 않는다.
- APP_ENV는 프론트의 배포 환경 선택 값이다. 백엔드에서 같은 의미의 별도 필수값을 중복 요구하지 않고 Spring 배포 프로파일을 환경 원본으로 사용한다.
- 테스트용 보조 프로파일을 나중에 추가하면 격리 DB·별도 자격 기준을 따른다. test라는 이름만으로 실제 접속 대상이 안전하다고 가정하지 않는다.
- MyBatis와 Redis의 외부 접속 정보는 환경별로 주입한다. prod에서 local 설정이나 메모리 세션으로 자동 fallback하지 않는다.
- Flyway는 schema 변경 원본이다. local 예제는 편의상 백엔드 시작 때 자동 적용하고 단일 테스트 계정을 사용할 수 있다. 실제 migration이 없으면 가짜 V1·회원 테이블·seed를 만들지 않고 기본 연결부터 검증한다. dev/stg/prod는 spring.flyway.enabled=false 및 별도 migration 자격/실행 경계를 유지한다. local 기본값과 Flyway 예외가 다른 프로파일로 전파되지 않게 검증한다.
- local은 DB jdbc:postgresql://127.0.0.1:25432/starter_local, Redis 127.0.0.1:26379 DB0, 사용자/비밀번호 testuser/1234를 설정 기본값으로 제공하고 환경변수로 override할 수 있다. 자격 생성·IDE 변수 복사 없이 local 프로파일 선택만으로 Docker 서비스에 연결한다. 프로파일 미선택을 local로 자동 해석하지 않는다. 포트 충돌·미기동 상태를 접속 오류로 구분하고 DB·Redis를 앱에서 자동 생성/시작하지 않는다.

### 프론트

- APP_ENV=local|dev|stg|prod는 빌드·개발 입력이다. 환경별 빌드 명령의 선택값이 기준이며 파일의 선택자 충돌·미지원 환경은 실패한다. production 실행은 npm start 하나로 통일하며 APP_ENV·공개 origin·환경 파일의 재주입을 요구하지 않는다. 현재 업무용 서버 실행 설정은 도입하지 않는다.
- NODE_ENV는 Next가 사용하는 development·production·test 의미로 유지한다. NODE_ENV=dev/stg/local 같은 사용자 정의 값은 넣지 않는다.
- npm run local은 local 환경의 HMR 개발 서버다. npm run dev·dev:local 별칭은 제공하지 않으며 사용자에게 노출되는 dev는 백엔드와 같은 공유 개발 환경만 의미한다. build:local·build:dev·build:stg·build:prod는 선택 환경의 production 빌드이고 npm run build는 local 기본 별칭이다. npm start는 Next 기본 start로 기존 빌드를 실행한다. start:<환경>와 자체 환경/BUILD_ID 마커는 제거한다. typecheck는 별도 셸 설정 없이 local 기준 실행한다. 공유 dev는 배포 환경 이름이며 Next 개발 모드가 아니다.
- frontend/config/env/.env.local·.env.dev·.env.stg·.env.prod 중 선택한 파일을 Node 내장 API로 빌드·개발 시 공급한다. 추가 dotenv/cross-env 도구는 설치하지 않는다. 실제 파일은 비공개이고 각 .example만 공개한다. 파일 대신 외부 빌드 환경변수도 사용 가능하며 일반 값은 외부 입력이 우선한다. 명령이 환경을 선택한다. root .env 파일 일괄 금지 같은 추가 정책은 제거하고 Next 기본 우선순위를 존중한다. .env.development.local은 개인 개발 보조용이며 .env.local은 production에도 로드되므로 환경 전용값을 공통 파일에 두지 않는다. 기존 비공개 파일을 덮거나 내용·비밀을 출력하지 않는다.
- 설정 추가 시 이름·기본값·필수 환경·소비 위치·빌드/실행 시점·공개 여부를 정의한다. 현재 배포 차이는 빌드에 반영하고 Next 설정·문서 보안 정책이 production 실행 시 환경변수 없이 동작하도록 한다. Next 기본 빌드 기능을 우선 사용하며 비밀·내부 주소를 next.config.env나 NEXT_PUBLIC_로 일괄 복사하지 않는다. 빌드 입력 전체를 산출물 JSON으로 저장하는 자체 환경 복원 체계를 만들지 않는다.
- 브라우저 호출은 공통 /api다. LOCAL_API_ORIGIN은 local 개발/빌드에서만 IDE 백엔드 전달 대상으로 사용하고 기본값은 http://127.0.0.1:8080이다. loopback HTTP/HTTPS origin만 허용하고 자격·path·query·fragment를 거부한다. 공유 환경은 ingress가 전달하고 frontend 내부 upstream을 요구하지 않는다.
- 브라우저 API 주소는 상대 /api 경로로 유지한다. 서버 목적지·배포 origin·비밀값을 NEXT_PUBLIC_로 복사하지 않는다.
- NEXT_PUBLIC_는 빌드 시 고정된다. 환경마다 달라지는 서버 접속값은 서버 실행 설정에서 읽고, 환경 설정을 바꾸려고 소스나 생성 client를 수정하지 않는다.
- HMR/eval 등 개발 CSP 완화는 APP_ENV=local이면서 Next 개발 모드인 경우만 허용한다. local에서 production 빌드를 시험할 때도 스크립트 정책은 production 기준으로 확인하고 HTTP에 맞는 HSTS/upgrade 예외만 적용한다.

공식 근거: [Spring 프로파일](https://docs.spring.io/spring-boot/reference/features/profiles.html), [Next 환경변수](https://nextjs.org/docs/app/guides/environment-variables). 환경 이름·파일 구성·검증 정책은 프로젝트 선택이다.

## 4. 로컬 실행의 공통 경계

- 프론트 호스트 포트 기본 3000, Spring 기본 8080, PostgreSQL 25432, Redis 26379로 정한다. 호스트 외부 공개 없이 루프백 기준으로 준비하고 충돌 시 설정으로 변경한다.
- 브라우저 진입은 local의 Next 서버다. /api/**는 로컬 개발용 전달 경로에서 Spring으로 보내며 /api 접두사를 유지한다. 브라우저가 backend 포트로 직접 호출하도록 별도 CORS 예외를 기본 추가하지 않는다.
- dev/stg/prod는 단일 HTTPS 출처의 진입 프록시가 같은 경로를 Spring에 전달하는 기준이다. 실제 프록시 제품·운영 주소 설정은 해당 배포 단계다. local 전달 기능을 운영 인프라로 오해하지 않는다.
- npm·Gradle Wrapper의 개발/빌드/검사/실행 명령을 제공하고 실제 동작을 확인한 명령만 README에 기록한다.
- Docker Compose는 DB·Redis 준비 도구다. migration·앱 시작은 Docker 자동 감지에 의존하지 않고 외부 DB/Redis 설정으로도 가능해야 한다.
- .gitignore·필요한 .dockerignore는 실제 비밀 파일·로그·빌드·생성 중간 산출물을 만들기 전에 적용한다. 안전한 .env.example만 공개하고 실제 자격·개인 경로를 포함하지 않는다.

## 5. 인증의 1차 준비 수준

Spring Security + Spring Session Redis + HttpOnly 세션 쿠키 + CSRF를 보편적인 브라우저 인증 기반으로 채택한다. sticky session에 기대지 않으며 쿠키·namespace·serializer·SecurityContext 저장 계약을 모든 인스턴스가 공유하게 한다.

1차는 기본 SecurityFilterChain·필터 오류 경계·쿠키 설정·CSRF 저장소/발급 처리 기반·Redis 세션 저장과 안전한 직렬화를 구성한다. Boot 자동 생성 사용자·고정 비밀번호·메모리 로그인으로 빈 부분을 메우거나 모든 /api를 permitAll로 열지 않는다. 사용자 인증이 없는 보호 자원은 공통 401/403 경로로 안전하게 거부한다.

실제 로그인 방식은 다음 인증 단계에서 정한다. 기본으로 검토할 일반 로그인은 Spring Security의 표준 사용자명/비밀번호 인증을 공유 세션에 연결하는 방식이다. 외부 SSO는 필요 시 OIDC 또는 SAML, 고객 Windows/agent는 해당 제품의 지원 경계로 연결한다. 인증 수단과 내부 사용자·권한·공유 세션을 분리하며 모든 SDK를 미리 설치하지 않는다.

프레임워크가 세션에 저장하는 실제 타입을 기준으로 JSON serializer·Security 모듈·허용 타입을 설정한다. Java native serialization이나 무제한 polymorphic 허용으로 임시 구현하지 않는다. 향후 SAML/OIDC 객체의 호환성까지 이미 보장했다고 표현하지 않는다.

CSRF·로그인·현재 사용자·로그아웃의 제품 경로와 payload는 인증 단계에서 확정한다. 1차에 공개된 빈 인증 endpoint나 가짜 로그인·권한 모델을 만들지 않는다. 실제 A/B 로그인·callback·권한 철회 시험은 그 기능을 붙일 때 [AF 기준](authentication-foundation.md)으로 수행한다.

## 6. 1차 완료 판단

- 버전표 범위만 설치되고 프론트 lockfile·backend Wrapper/BOM/잠금·무결성 기준이 기록됨.
- 두 모듈의 빌드·타입·lint와 필요한 기본 테스트 결과가 있음. 업무 코드가 없다는 이유로 가짜 예제를 만들지 않음.
- 네 환경의 설정 원본·필수값·프로파일 선택·비밀 주입·개발 예외가 분리됨. 실제 dev/stg/prod 접속 성공은 완료 조건이 아님.
- API 공통 모델·반환 편의·오류 경계가 [응답 계약](api-contract.md)과 일치함. 미분류 예외·필터·/error fallback에서도 원문 메시지·스택·SQL·내부 경로가 응답에 없고 CFG06의 비노출 설정이 네 환경 모두에서 유지됨.
- PostgreSQL/MyBatis·Redis 세션 설정과 로컬 준비 절차가 있음. 연결·저장 동작 확인은 승인된 격리 대상에서 수행하며 미실행은 분명히 기록함.
- 기본 보안 거부·CSRF·쿠키·공통 오류·시작 설정 검증과 프론트 공통 요청 경계를 확인함.
- 전체 ASVS·실제 로그인·SSO·업무 SQL·모든 공통 컨트롤의 통과를 1차 완료에 요구하거나 완료된 것으로 표시하지 않음. 실제로 구현한 기반에 해당하는 검증은 유지함.
- 미구현 항목을 기능 완료로 쓰지 않고 다음 단계로 인계함. DB 변경 실행은 공통 DB 승인 규칙을 따름.

## 7. 이번 단계에 추가 질문이 필요한가

현재 위임·확정한 선택으로 1차 세팅의 설계 기준을 작성할 수 있어 추가 질문은 필요하지 않다. 고객 배포 도메인·IdP·회원 schema·역할 표·화면/공통 컨트롤 상세는 해당 기능을 시작할 때 결정한다. 구현 세션은 이 문서의 경계 안에서 기반을 만들고 해당 기능의 빈 부분을 임의로 확장하지 않는다.
