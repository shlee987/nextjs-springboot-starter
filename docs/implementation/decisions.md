# 결정과 적용 범위

기준일: 2026-10-30. 이 문서는 확정한 선택과 추가 구현 범위를 관리한다. 날짜별 과거 기록과 현재 상태를 구분하며 실제 결과는 현재 현황·모듈 검증표를 따른다.

## 사용자 확정 사항

| 항목 | 내용 |
| --- | --- |
| 목적 | 계속 재사용할 아주 기본적인 설정·보안의 묶음. 선택 기능은 설치하지 않고 문서로만 참조 |
| 프론트엔드 | Next.js, React, TypeScript |
| UI 도구 | Radix Primitives 기본 포함. Radix Themes·Tailwind·shadcn/ui는 기본 미포함 |
| 데이터 그리드 | AG Grid Community 기본 포함. MIT 라이선스 범위만 사용하고 Enterprise 패키지·기능은 기본 제외 |
| 차트 | Apache ECharts 기본 포함. Apache 2.0 라이선스 고지 유지. 공통 차트로 재사용 |
| 공통 UI 범위 | 자주 쓰는 컨트롤의 공통 구현은 전체 프로젝트 목표이며 1차에는 도구·스타일 기반만 준비. HTML·CSS 퍼블리싱 적용과 화면에서의 간편한 재사용을 기준으로 설계. Radix Primitives를 동작 기반으로 사용하고 공통 그리드·차트도 포함. 세부 목록·옵션·디자인 구조는 해당 작업 시 설계 |
| 백엔드 | Java, Spring Boot, API·업무·데이터 처리 |
| 저장소 | 프론트·백엔드 한 저장소, GitHub 공개 |
| 지침 | docs/common-rules의 규칙 적용 |
| 작업 분리 | 프로젝트에는 공개 결과물만, 개인 작업 자료는 외부 작업 공간 |
| 현재 역할 | 설계문서 작성. 구현은 별도 프론트·백엔드 세션에서 진행 |
| 현재 단계 | 1차 세팅 완료 후 백엔드 공통 기반 최종 설계 확정. backend-foundation.md가 이번 범위와 상세 계약을 소유하며 구현은 별도 진행 |
| 실행 환경 이름 | local/dev/stg/prod. backend Spring 프로파일, frontend APP_ENV. 프론트는 npm run local·환경별 빌드·선택 설정 파일과 공통 npm start를 제공. dev는 공유 개발 환경만 의미하며 Next 내부 NODE_ENV/CLI 모드와 구분. 상세는 initial-setup.md |
| 공통 API 양식 | api-contract.md의 성공·오류·페이지 모델과 간편 반환 함수. HTTP 상태 유지 |
| 실제 예외 정보 | 1차 필수. 모든 환경에서 API 응답에 예외 원문·스택·SQL·내부 경로를 노출하지 않음. 공통 오류와 traceId만 공개, 진단은 정제한 서버 로그 |
| 서버 데이터 상태 | TanStack Query 기본 포함. 브라우저 서버 데이터 관리, 요청 전송은 Axios |
| 브라우저 HTTP 요청 | Axios 기본 포함. 공통 인스턴스로 쿠키·CSRF·timeout·오류 처리를 통일. Next 서버 fetch와 경계 구분 |
| API 클라이언트 생성 | Orval 기본 포함. OpenAPI에서 타입·Axios 호출 함수·TanStack Query Hook 생성. 다른 생성 도구는 문서 전용 대안 |
| 공유 클라이언트 상태 | Zustand 기본 포함. 서버 데이터·폼 상태와 중복 보관하지 않고 UI 공유 상태에 사용 |
| 폼 관리 | React Hook Form 기본 포함. 입력값·검증 결과·제출 상태 관리 |
| 프론트 유효성 검증 | Zod + @hookform/resolvers 기본 포함. 폼 검증 규칙과 React Hook Form 연동 |
| 서버 유효성 검증 | Bean Validation 기본 포함. 오류 코드·문구 관리와 공통 응답 처리 포함 |
| 테스트 도구 정책 | JUnit + Spring Boot Test 기본 포함. Spring 설정·DataSource·Mapper·트랜잭션을 테스트에서 사용. AssertJ 등 추가 도구는 문서 전용 선택사항 |
| 테스트 결과 검증 | AssertJ는 기본 미포함. 값·목록·객체·예외 자동 검증의 편의가 필요할 때 도입 검토 |
| 보안 테스트 보조 도구 | Spring Security Test는 기본 미포함. 설명·검증 기준은 유지하고 업무 권한 자동 검증이 필요할 때 도입 검토 |
| 테스트 외부 서비스 자동화 | Testcontainers는 기본 미포함. 격리 DB/Redis의 자동 생성·정리가 필요할 때 도입 검토 |
| 컴포넌트 테스트 보조 도구 | React Testing Library는 기본 미포함. 설명은 유지하고 컴포넌트 동작의 반복 자동 검증이 필요할 때 도입 검토 |
| 도구 범위 | 현재 예제에 직접 도움이 되는 최소 구성. Playwright·Actuator 및 통합 모니터링 기본 제외, 추가 도구는 사용 사례로 판단 |
| DB 접근 | MyBatis 단독 사용. 서비스 어노테이션으로 업무 트랜잭션 경계 명시 |
| Java 반복 코드 감소 | Lombok 기본 포함. 필요한 생성자·접근자만 생성하고 민감 필드 출력·무차별 변경 메서드 생성 금지 |
| 객체 변환 코드 생성 | MapStruct는 기본 미포함. 반복되는 객체 변환이 많아질 때 검토하는 문서 전용 선택사항 |
| DB 스키마 준비 | Flyway 기본 포함. 로그인·기본 화면 예제의 테이블 생성·변경 SQL 적용. 로컬 합성 데이터와 운영 스키마 분리 |
| Redis | 인증 상태 저장소 기본 redis, jdbc 선택 지원. 일반 API JWT, 외부 로그인용 Spring Session 조건부 공존 |
| 인증 확장 | 모든 SSO를 미리 구현하지 않고 인증 수단·내부 사용자/권한·공유 세션을 분리 |
| 버전 선정 위임 | 2026-10-26 사용자 위임에 따라 편의성 기준 DB와 최신 LTS OpenJDK, 호환되는 최신 안정 버전 조합을 설계자가 선정. [확정 버전표](stack-versions.md) 적용 |
| 로컬 DB 제품 | PostgreSQL 18.6 확정. Redis 8.10.2와 함께 로컬 준비 |
| 빌드·패키지 도구 | Gradle Wrapper 9.8.2 + npm으로 전환(정확 버전은 버전표 참조) |
| Docker 범위 | starter-project 그룹의 로컬 DB·Redis. 공개 테스트 testuser/1234 기본값, 앱은 IDE에서 실행. 실제 환경 배포의 필수 조건으로 만들지 않음 |
| local 백엔드 편의 | local 프로파일에 Docker 접속 기본값 제공, Flyway 자동 적용 허용. dev/stg/prod 필수 외부 설정과 별도 migration은 유지. 임의 회원 테이블·seed 선구현 없음 |

## 설계 기본안과 결정 관문

기술 구성은 [버전표](stack-versions.md)로 확정했다. 아래는 채택 기준과 후속 상세 결정의 경계를 관리한다. 사용자 직접 선택과 사용자 위임에 따른 설계자 선정을 구분하며, 미정인 예제·운영 세부 사항을 기본 스택 미확정으로 해석하지 않는다.

| ID | 기본안 또는 필요한 결정 | 영향을 받는 작업·해소 시점 |
| --- | --- | --- |
| D01 | 해소: OpenJDK Eclipse Temurin 25 LTS(HotSpot; OS별 실제 패치는 stack-versions.md 참조), Node 24.22.1 LTS, Next 16.4.3·React 19.3.1·TS 6.0.3, Boot 4.1.2 및 관리 BOM. 전체 정확 버전은 stack-versions.md 단일 기준 | 구성 선정 완료. 의존성 설치·lockfile·실행 호환성 검증은 후속 구현 |
| D02 | 채택 기준: Next App Router + Node 서버 동적 렌더링, 브라우저에는 단일 HTTPS 출처 노출 | CSP·라우팅 세부 설정은 후속 설계·구현 |
| D03 | 채택 기준: 같은 출처의 /api/**를 신뢰 프록시가 Spring으로 전달. Next는 HTML·정적 자원 제공, API 인증·권한은 Spring 책임 | 프록시 제품·네트워크·쿠키 세부 설정은 D06과 함께 확정 |
| D04 | 해소: 자체 로그인 + JWT/갱신 토큰 + HttpOnly 쿠키/CSRF. 기본Redis·선택JDBC 상태 저장, allow/deny-new/replace-old 동시 로그인. 외부 인증 진행용 Spring Session 공존 | authentication-foundation.md·backend-data-model.md·backend-handoff.md 적용. 실제 외부 연동/MFA/가입/복구는 별도 |
| D05 | 제품·버전 해소: PostgreSQL 18.6 + MyBatis 단독 + Flyway 12.4.0 및 PostgreSQL 지원 모듈, Redis 8.10.2 + Spring Session Redis. BOM·starter 관리 버전은 버전표 적용 | 실제 스키마·DB별 설정·세션 직렬화·운영 HA는 해당 후속 상세 설계 |
| D06 | 배포 OS·프록시·TLS 종료·컨테이너·클라우드·운영 도메인 확정 | 실행 가능한 인프라 설정 작성 전. IIS/Windows/SQL Server는 현재 필수 기술이 아니다 |
| D07 | CI 기본안 GitHub Actions, 임시 호스팅 runner, 공개 PR과 배포 작업 분리 | 워크플로·저장소 보호 규칙 작성 전 |
| D08 | 기본 예제에 파일 업로드·웹훅·외부 스크립트·임의 URL 조회·배치 미포함 | 도입 시 해당 조건부 설계를 활성화하고 API·검증 계약 추가 |
| D09 | 해소: 사용자/부서/역할/권한 관계·기본 권한표·API는 최종 설계 적용. 기본 멀티테넌트 기능 없음, 요청 tenantId로 범위 확장 금지 | backend-data-model.md·authentication-foundation.md·backend-handoff.md. 고객별 테넌트/업무 데이터 범위는 도입 시 설계 |
| D10 | 보안 검증 목표는 일반 웹/API 위험에 맞는 ASVS L2 지향 기본안. 조항 분류·설계 연결은 0.2에서 완료. 데이터 보존·RPO/RTO·운영 책임자와 실제 조항별 충족 결과는 이후 확정 | ASVS 준수 주장과 실제 운영 배포 전 |
| D11 | 도구·버전 해소: Radix Primitives(radix-ui 1.8.1), AG Grid Community/React 36.2.0, ECharts 6.1.0. 일반 CSS·CSS Modules·CSS 변수 채택 | 컨트롤 목록·옵션·스타일·Next/CSP 실제 동작은 공통 UI 작업 시 설계·검증 |

## 기준 데이터 흐름

P1 기본안: 브라우저 → HTTPS 진입 프록시 → Next HTML 또는 Spring /api/** → 비공개 DB·세션 저장소.

- 브라우저는 DB·관리 포트에 접근하지 않는다.
- 프록시는 API를 고정 upstream으로 전달한다. 사용자 URL을 upstream으로 삼지 않는다.
- Next 서버가 Spring을 호출하면 고정 내부 목적지만 사용하고 현재 요청의 필요한 인증 정보만 전달한다. 사용자 제공 신원 헤더는 믿지 않는다.
- 서비스가 여러 인스턴스면 세션을 공유하거나 명시적인 대체안을 확정한다. 기본 메모리 세션을 다중 인스턴스 운영에 그대로 사용하지 않는다.
- P1과 다른 정적 export·분리 출처·Bearer token·외부 IdP 구성은 별도 설계 변경이다. CSP·쿠키·CSRF·CORS·캐시를 함께 재검토한다.

## 현재 진행 가능한 작업

[1차 세팅](initial-setup.md)에 정한 기반은 착수할 수 있다. 공통 응답·프로파일·프론트/백엔드 도구와 인증·데이터 연결 기반을 준비하며, 기능별 미정 항목 때문에 전체 세팅을 보류하지 않는다. 개별 로그인·업무·UI·실제 운영 배포는 해당 관문을 해소한 뒤 구현한다.

결정 기록에는 ID, 선택, 이유, 영향 문서, 버전, 확인일을 남긴다. 예외는 verification.md의 예외 기록 기준으로 관리한다.

## 공통 보안 검토 0.2의 결정 영향

[검토 결과](security/review-report.md)와 [보완 통제](security/controls.md)를 적용한다. D04는 일반 사용자 MFA·비밀번호 변경·복구·동시 세션을, D06은 HSTS 하위 도메인·내부 TLS·서비스 신원·vault를 함께 결정한다. 해당 차이를 해소하지 않고 L2 충족으로 표시하지 않는다. 일반 보안 항목의 조사·대조는 완료했으며 제품별 실행 설정과 업무 계약이 다음 단계다.
## 기본 라이브러리·로컬 DB 방향

2026-10-22 사용자 방향: 주력 스택에 Swagger 등 기본 라이브러리를 포함하고 Docker 기반 DB로 공개 저장소를 간편하게 시험할 수 있게 한다.

[기본 라이브러리 역할](technology-stack.md)에 기본 기능과 문서 전용 선택 도구를 분리했다. TanStack Query·React Hook Form·Zod·Bean Validation은 기존 채택 결정을 유지한다. 2026-10-26 최소 재사용 묶음 정책에 따라 JUnit과 Spring Boot Test는 기본 포함하고 AssertJ 등 추가 테스트 도구는 기본 설치에서 제외한다. Lombok은 Java 반복 코드를 줄이기 위한 기본 도구로 포함한다. Flyway는 로그인·기본 화면용 DB 스키마 준비에 사용하도록 확정했다. Playwright·Actuator·통합 모니터링도 기본 제외하며, 실제 프로젝트에서 필요한 도구와 적용 이유를 제안한다. 미결정 도구를 기본 채택으로 간주하지 않는다. [로컬 환경 설계](local-environment.md)는 확정한 PostgreSQL + Redis + Compose와 개발 DB에서 분리한 통합 검증 DB 방향을 관리한다.

D01의 정확 버전과 D05의 로컬 DB 제품·버전을 2026-10-26 확정했다. [버전표](stack-versions.md)는 사용자 위임에 따라 선정한 기본 조합의 단일 기준이다. 데이터 접근은 MyBatis 단독이며 [데이터 접근 계약](persistence.md)을 적용한다. 공유 세션은 Spring Session Redis를 사용하고 실제 직렬화·운영 topology는 후속 설계한다. 로컬 Swagger UI는 운영 비공개 기준을 지키며, Docker 로컬 구성은 D06 운영 인프라 선택을 대신하지 않는다. 이 방향 기록은 실제 컨테이너·migration·DB 변경 실행 승인이 아니다.

## Redis와 환경 독립성

[인증 확장·Redis 공유 세션](authentication-foundation.md)을 공통 기반으로 적용한다. D04의 실제 인증 수단·provider별 callback/쿠키·MFA는 선택 관문으로 남긴다. [로컬 환경](local-environment.md)의 Docker는 준비 편의이며 실행 필수 의존성이 아니다. 운영 DB/Redis는 동일한 설정 계약으로 외부 서비스에 연결하고 Compose hostname·volume·Docker socket을 제품 코드의 전제로 두지 않는다.

## 기본 구성 확정 기록 — 2026-10-26

최신 안정판과 호환성의 교집합을 선택했다. TypeScript 7·ESLint 10은 현재 lint 의존성 지원 범위 때문에 제외하고 TS 6.0.3·ESLint 9.39.5를 채택했다. Node 26 LTS 전환 전이므로 24 LTS를 사용한다. 대체 조합은 [별도 문서](stack-alternatives.md)에 조건과 교체 영향을 기록하며 기본 설치하지 않는다. 기본 예제·UI 상세·운영 배포 세부는 사용자의 요청대로 후속 작업에 남긴다. 이번 확정은 패키지 설치·DB 실행·migration·커밋·push 승인이 아니다.

## 단계 범위 확정 — 1차 프로젝트 세팅

[1차 범위와 인계 기준](initial-setup.md), [공통 API 응답](api-contract.md)을 적용한다. 프론트·백엔드는 별도 구현 세션에서 준비하며 성공/오류 반환 방식과 local/dev/stg/prod 환경을 공유한다. 상세 로그인·업무 API·schema·공통 컨트롤은 순서대로 설계·구현한다. 네 환경의 실제 운영 자격·주소·IdP·업무 권한 미정은 이번 세팅의 추가 질문이나 착수 차단 항목이 아니다.

## 백엔드 공통 기반 확정 — 2026-10-29

[최종 설계](backend-foundation.md)가 1~4단계 결정을 소유한다. 이전 P1 세션 API 인증·JWT 미도입·동시3개·점증 지연은 이번 단계에서 대체한다. 문서의 이전 날짜 기록은 역사적 선택이며 이번 구현에 재적용하지 않는다.

일반 API는 JWT, 인증 상태 redis(default)/jdbc, 필요 시 Spring Session 공존. 사용자별 TOKEN_VERSION 컬럼 없이 상태 저장소 분리. 외부 로그인 임시 세션 JDBC 테이블은 전용 schema/history로 제거 가능. 날짜3형식·UTC·밀리초, 큰숫자 문자열·프론트 정밀계산 후속예제, 실제 대문자 언더바·MyBatis 자동camel매핑 확정. local 콘솔/상세SQL, 공유환경 전체+ERROR 파일·회전·압축·보관. 접근/보안 이력과 실패정책·최소 조회API, 키/갱신/폐기/동시정책·검증범위를 확정했다. 공통 룰 사본은 이번 범위에서 바꾸지 않는다.

설계 문서만 완료했다. 실제 구현·DB 변경 실행·새 라이브러리 설치·기능 검증·작업자 메시지·커밋·push는 이번 정리에서 수행하지 않았다.


## 후속 공통 개선 확정 — 2026-10-30

사용자가 PC/모바일 UI와 공통 컨트롤 옵션·라디오 표시·작업별 중복 실행 방지, 쉬운 문구·local 로그, 공격 대응 및 파일 업로드 구현을 승인했다. [UI 보완](ui-component-review.md), [문구·로그](readability-and-local-logs.md), [공격 대응·파일](attack-defense-and-files.md)이 이번 추가 범위를 소유한다. 새 UI 프레임워크·클라우드 저장소·전역 문자열 치환 필터는 도입하지 않는다. 과거의 문서만 작성했다는 기록은 그 날짜의 이력이다. 현재 구현·실행·독립 리뷰 상태는 [현재 현황](foundation-status.md)을 따른다. 커밋·push는 사용자가 직접 진행할 예정이며 수행하지 않는다.
