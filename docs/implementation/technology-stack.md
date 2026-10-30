# 기본 라이브러리 역할과 통합 원칙

> 2026-10-29 현재 인증 계약은 [JWT·Redis/JDBC·공유 세션 공존](authentication-foundation.md), 로그는 [로그·감사](backend-observability.md)로 갱신했다. 본문의 D04 후속/JWT 자동 미도입 표현은 1차 구성 선정 당시 설명이며 이번 명시 채택을 취소하지 않는다. 기존 정확 버전 유지, 새 의존성의 설치 증거는 아직 없다.

작성일: 2026-10-22 / 검토 갱신: 2026-10-26 / 상태: 기본 구성·버전 선정 완료. 설치·잠금 파일·호환성 실행 검증 전.

사용자 방향: Next.js·React·TypeScript + Java·Spring Boot API, 공개 저장소에서 쉽게 시험할 수 있는 Docker 기반 DB. Redis 도입과 Docker의 로컬 편의 도구 범위는 사용자 요구로 반영한다. 데이터 접근은 MyBatis 단독 사용으로 사용자 확정이다. TanStack Query, React Hook Form, Zod + @hookform/resolvers, Bean Validation, Zustand, Axios, Orval 기본 포함도 사용자 확정이다. 2026-10-26 사용자의 선정 위임에 따라 PostgreSQL·OpenJDK와 나머지 기본 도구의 안정 버전을 확정했다. 정확 버전은 [확정 구성·버전표](stack-versions.md), 교체 조건은 [상황별 대체 조합](stack-alternatives.md)이 소유한다. 선택 도구는 계속 문서 전용이다.

## 선택 원칙

계속 재사용할 기본 기능만 포함한다. 선택사항은 문서 전용 참조이며 패키지·샘플 구현·설정·CI를 미리 넣지 않는다. JUnit과 Spring Boot Test는 기본 수준으로 포함한다. AssertJ 등 추가 테스트 도구와 Playwright·운영 모니터링은 기본 미포함이다. 실제 업무 로직·반복 회귀·환경 요구가 생기면 도입할 도구와 이유를 먼저 제안한다. 보안·트랜잭션 검증 기준은 유지하되 실행 방법과 자동화 여부를 구분한다. 같은 역할의 라이브러리는 하나를 선택한다. 부가 서비스나 상태 관리 도구는 실제 요구가 생겼을 때 추가한다. 이 문서의 채택 구성은 현재 프로젝트 기준이며 모든 웹 프로젝트에 필수라는 뜻이 아니다.

## 프론트엔드 채택 구성

| 역할 | 채택 구성 | 구현 계약 |
| --- | --- | --- |
| 프레임워크·언어 | Next.js App Router, React, TypeScript strict | 서버/클라이언트 경계와 nonce CSP·동적 렌더링 계약 준수 |
| 서버 데이터 상태 | TanStack Query — 사용자 확정 | 브라우저 API 조회·검색·페이지 전환·수정 후 갱신을 관리. 브라우저 요청 전송은 Axios 사용. Next 서버 조회와 불필요한 중복 금지 |
| API 클라이언트 생성 | Orval — 사용자 확정 | OpenAPI 명세에서 타입·Axios 호출 함수·TanStack Query Hook 생성. 이 문서의 Orval 계약 적용. 생성 코드는 공통 Axios 경계 사용 |
| 요청 전송 | Axios — 사용자 확정 | 브라우저 공통 인스턴스에서 /api·쿠키·CSRF·timeout·취소·오류 계약 단일화. 변경 요청 자동 재시도 금지. Next 서버 전용 fetch와 실행 경계 구분 |
| 공유 클라이언트 상태 | Zustand — 사용자 확정 | 여러 컴포넌트의 UI 상태 공유. 서버 데이터는 TanStack Query, 폼은 React Hook Form, 단일 컴포넌트 상태는 useState. Next 서버 요청 간 상태 공유 금지 |
| 폼 | React Hook Form — 사용자 확정 | 폼 상태·field 오류·submit 관리. 클라이언트 통과가 서버 검증을 대체하지 않음 |
| 런타임 검증 | Zod + @hookform/resolvers — 사용자 확정 | 입력 schema·필요한 외부 응답 경계 검증. TypeScript 단언만으로 안전 판정 금지 |
| 공통 UI 동작 기반 | Radix Primitives — 사용자 확정 | 자주 쓰는 공통 컨트롤 구현에 활용. HTML·CSS 디자인 적용을 기준으로 하며 세부 명세는 해당 작업 시 작성 |
| 데이터 그리드 | AG Grid Community — 사용자 확정 | 공통 그리드로 데이터·컬럼·이벤트·옵션을 연결해 재사용. Enterprise 패키지·기능 제외. 세부 명세는 해당 작업 시 작성 |
| 차트 | Apache ECharts — 사용자 확정 | 공통 차트의 기본 디자인·설정을 마련하고 데이터·옵션으로 재사용. 종류·연동·세부 명세는 해당 작업 시 작성 |
| 품질 | ESLint + Prettier + TypeScript 검사 | 공통 2칸/100자/빈 줄 규칙과 실제 설정의 충돌 검토. lint·format·typecheck 구분 |
| 프론트 기본 검사 | TypeScript + ESLint, 핵심 화면 수동 점검 | 실행 결과와 재현 절차 기록. 자동화로 이득이 있는 반복 검사가 생기면 Vitest + React Testing Library 도입 |
| 브라우저 흐름 확인 | 수동 점검 절차 | Playwright 의존성·브라우저 설치·자동 E2E job은 기본 미포함. 프로덕션 빌드에서 로그인·권한·CSRF·CSP·라우팅·폼 흐름 확인 |

TanStack Query는 HTTP client가 아니라 서버 데이터 상태 계층이다. 브라우저 API 호출은 공통 Axios 인스턴스를 사용하고 각 화면에서 별도 Axios/fetch 공통 경로를 만들지 않는다. Axios 오류는 공통 요청 경계에서 정규화하되 알림은 화면/공통 소유자 한 곳에서만 처리한다. Next 서버 호출은 서버의 fetch·캐시 계약을 유지하며 브라우저용 인스턴스의 쿠키·상태를 서버 전역에 공유하지 않는다. Next 서버 fetch와 브라우저 query가 같은 데이터를 무계획하게 중복 조회하지 않도록 화면별 소유자를 정한다. QueryClient를 서버 전역 singleton으로 두거나 개인 응답을 공유하지 않는다. logout/계정 전환 시 취소·캐시 정리, 기본 retry 정책을 API 계약에 맞춰 고정한다.

근거: [TanStack Query](https://tanstack.com/query/latest/docs/framework/react/overview), [React Hook Form](https://react-hook-form.com/get-started), [Zod](https://zod.dev/), [Next 테스트 안내](https://nextjs.org/docs/app/guides/testing). 기본 포함 범위는 현재 프로젝트 선택이다.

## 백엔드 채택 구성

| 역할 | 채택 구성 | 구현 계약 |
| --- | --- | --- |
| API | Spring MVC/Web starter | REST Controller·DTO·단일 오류 계약. WebFlux를 함께 기본 도입하지 않음 |
| 인증·권한 | Spring Security | 세션·CSRF·기본 거부·자원/필드 권한. D04에서 실제 인증 수단 확정 |
| 인증 상태/공유 세션 | Redis 기본 + JDBC 선택, Spring Session은 외부 로그인 진행 시 조건부 | 일반 API JWT, 토큰 상태와 임시 세션을 분리. 최종 인증 설계와 데이터 모델 적용 |
| 입력 검증 | Jakarta Bean Validation 지원 starter — 사용자 확정 | 타입·필수·길이·범위·중첩 검증과 업무 검증 구분. [오류 문구 관리 기본안](security/backend.md)의 API05 보완 적용 |
| DB 접근 | MyBatis-Spring Boot starter + JDBC driver | MyBatis 단독 사용 확정. PostgreSQL 제품·버전은 확정. [데이터 접근 계약](persistence.md)의 연결·트랜잭션·SQL·캐시·검증 적용 |
| 스키마 이력 | Flyway + PostgreSQL 지원 모듈 — 확정 | 로그인·기본 화면용 테이블 준비와 스키마 변경 이력. DDL은 migration 단일 소유. local 합성 seed와 운영 DDL 분리, 실행은 Docker와 독립 |
| API 명세·시험 UI | springdoc-openapi + Swagger UI | 로컬 전용 기본안. OpenAPI schema·공통 오류·인증·CSRF 동작을 실제 API와 일치시킴 |
| 운영 모니터링 | 기본 미포함 — 사용자 확정 | Actuator 및 Prometheus·Grafana는 제외. 운영 환경과 기존 도구 확인 후 별도 요청 시 설계 |
| 로그 | Boot 기본 SLF4J/Logback 계열 | 비밀/개인정보 정제·trace·운영 UTC JSON은 기존 Boot 기능 활용. 중앙 감사 수집/APM 연결은 운영 환경 결정 후 적용하며 별도 제품 기본 설치 없음 |
| Java 코드 보조 | Lombok — 사용자 확정 | 생성자 주입용 @RequiredArgsConstructor와 필요한 @Getter 등 활용. 무차별 @Data·@Setter·toString 생성 금지. DTO 성격에 맞으면 record 사용 가능 |
| 검증 | JUnit + Spring Boot Test, 빌드·정적 검사·문서화한 동작 확인 | Spring 테스트는 테스트 전용 DataSource·Mapper·트랜잭션을 사용. 그 외 선택 테스트 도구와 별도 자동화 job은 기본 미포함. 업무·입력·권한·연결·롤백 검증의 방법과 결과를 기록 |
| DB 동작 확인 | 격리된 실제 DB에서 확인 | 실제 SQL·트랜잭션·임시 테이블 검증 기준 유지. 자동화는 필요 시 도구를 선정하고 개인/운영 데이터로 검증하지 않음 |

CRUD·검색·복잡한 조회는 MyBatis로 구현한다. 상세 기준은 [데이터 접근 설계](persistence.md)를 따른다. 서비스 트랜잭션과 동일 연결 공유를 유지하고 스키마 생성 경로를 병행하지 않는다.

Flyway가 스키마를 소유하고 schema.sql 자동 생성, JDBC 세션 변형의 자동 DDL을 병행하지 않는다. 기본 Redis 세션에는 JDBC session table을 만들지 않는다. PostgreSQL 지원 모듈과 Boot BOM의 관리 버전은 확정 버전표를 따른다. [Boot DB 초기화](https://docs.spring.io/spring-boot/how-to/data-initialization.html), [Boot SQL](https://docs.spring.io/spring-boot/reference/data/sql.html)

## Swagger/OpenAPI 상세 계약

OpenAPI는 API 계약 명세이며 Swagger UI는 이를 읽는 시험 화면이다. Spring에서는 springdoc-openapi-starter-webmvc-ui를 사용한다. Boot 4.1.2 + springdoc 3.2.0 조합으로 확정했으며 [버전표](stack-versions.md)에 근거를 기록했다. 다른 Boot 계열로 교체할 때는 springdoc 호환 계열도 함께 변경한다. [springdoc 공식 문서](https://springdoc.org/)

| 환경 | UI·OpenAPI endpoint |
| --- | --- |
| local | 루프백에서 활성화. 합성 데이터·로컬 자격만 사용 |
| test/CI | 계약 export와 검증을 위한 격리 프로필. 운영 자격/데이터 미사용 |
| dev/stg | 기본 비활성. 필요 시 명시적 관리 인증·네트워크 제한 |
| prod | springdoc.api-docs.enabled=false, springdoc.swagger-ui.enabled=false. 프록시와 API에서도 관련 경로 차단 |

운영 검사에는 /v3/api-docs, 하위 YAML/group/config 경로, /swagger-ui.html, /swagger-ui/**의 실제 활성 경로를 포함한다. 제품·설정별 경로가 달라지면 정확한 경로 목록을 갱신한다. UI만 숨기고 JSON spec은 열어 두지 않는다.

“Try it out”은 실제 변경 요청이다. 보호 API를 공개하거나 CSRF를 끄지 않는다. 세션 로그인·CSRF token 전달·로그아웃 후 재발급을 개발 문서 화면에서도 시험한다. Swagger 인증 상태 영속 저장은 기본 비활성, 외부 validator·분석·CDN은 사용하지 않는 기본안이다.

Swagger HTML을 JSON용 CSP로 일괄 처리하지 않는다. 로컬 Swagger 자원은 경로별 HTML 헤더 정책을 별도로 정의·검증하고 운영 Next CSP를 완화하지 않는다. 헤더 문서의 로컬 예외를 명시한 범위만 적용한다. API 명세 예제에 실제 토큰·개인정보·내부 운영 URL을 넣지 않는다.

생성된 명세에 필드별 권한·min/max·required/null·오류코드·보안 scheme·페이지 상한이 담겨야 한다. annotation만으로 검증/권한이 구현됐다고 판단하지 않는다.

## 조건부·선택 확장

| 도구 | 추가하는 조건 |
| --- | --- |
| AssertJ | 문서 전용 선택사항 — 사용자 결정. 목록·객체·예외 검증 표현의 편의가 필요할 때 검토. JUnit 기본 검증으로도 구현 가능 |
| Spring Security Test | 기본 미포함 — 사용자 결정. 소유권·테넌트·업무 상태 등 직접 작성한 권한 로직이나 반복 보안 회귀를 자동 검증할 때 검토. 테스트 사용자·권한·CSRF 요청 준비를 지원하며 실제 로그인·Redis 공유 세션 검증을 대체하지 않음 |
| Vitest | 기본 미포함. 프론트 함수·검증 규칙·Hook 등의 반복 회귀 검사가 필요할 때 검토 |
| React Testing Library | 기본 미포함 — 사용자 결정. 입력·클릭·서버 오류 표시 등 직접 연결한 컴포넌트 동작의 반복 회귀 검사가 필요할 때 Vitest 등 테스트 실행 도구와 함께 도입 검토 |
| Testcontainers | 기본 미포함 — 사용자 결정. 격리 DB/Redis 자동 생성·정리가 필요할 때 검토. 별도 테스트 DB를 명시적으로 준비하는 경로를 유지하고 기본 실행과 CI의 필수 조건으로 넣지 않음 |
| Spring Session JDBC | 후속 설계에서 redis/jdbc 선택 지원 확정. 외부 로그인 진행용이며 enabled일 때만 전용 schema에 생성, 토큰 관리 테이블과 독립 |
| Redis 일반 캐시·rate limiter | Redis 세션은 기본 포함. 추가 용도는 별도 계약·namespace·자격·용량/장애 격리를 설계한 뒤 사용 |
| 다른 API 생성 도구 | 문서 전용 대안. OpenAPI Generator·swagger-typescript-api·openapi-typescript 비교는 이 문서의 Orval 계약 참조. 기본 설치하지 않음 |
| MapStruct | 문서 전용 선택사항 — 사용자 결정. 반복·복잡한 DTO 변환이 실제로 많아질 때 검토. 기본 의존성·매핑 코드 미포함 |
| Radix Themes / Tailwind CSS / shadcn/ui | 문서 전용 대안. 현재는 Radix Primitives와 일반 CSS 방향이며 기본 설치하지 않음. 별도 요구가 생기면 디자인 소유·퍼블리싱·CSP 영향을 비교 |
| Redux Toolkit + React Redux | 문서 전용 대안. 고객 환경·팀 규칙에 따라 Zustand를 교체할 때 검토. 같은 공유 상태를 두 도구에 중복 보관하지 않음 |
| OAuth/OIDC SDK | 외부 IdP 연동 때만 추가. 자체 로그인 JWT는 최종 인증 설계로 채택했으며 외부 SDK와 구분 |

근거: [Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html), [openapi-typescript](https://openapi-ts.dev/introduction). 나머지 선택 도구는 현재 채택·호환성 검증한 상태가 아니며 실제 선정 시 공식 문서와 버전을 확인한다.

## 버전과 실행 검증 경계

[확정 구성·버전표](stack-versions.md)를 단일 기준으로 사용한다. npm·Gradle Wrapper·OpenJDK·PostgreSQL·Redis와 기본 라이브러리 버전은 선정 완료다. 호환성 때문에 최신판에서 조정한 TypeScript·ESLint도 해당 문서에 기록했다.

설치·lockfile 생성·Next/Java 빌드·Orval 출력 타입 검사·DB/Redis·CSP 실행 검증은 후속 구현 단계다. 패키지 교체·예외는 문서와 검증 기준을 함께 갱신하며 기본 선택 도구를 임의로 늘리지 않는다.

## 런타임의 Docker 독립성

공유 세션의 동작 계약은 [인증 기반](authentication-foundation.md)을 따른다. Redis는 외부 네트워크 서비스이며 Docker에서 실행한 Redis인지 애플리케이션이 알 필요가 없다. PostgreSQL도 동일하다.

Docker Compose 연동과 조건부 Testcontainers는 로컬/테스트 도구다. production dependency·자동 시작·Docker daemon 접근·경로 하드코딩으로 제품 런타임에 전파하지 않는다. 애플리케이션은 표준 JAR/Node 실행 또는 별도 배포 이미지 중 환경에 맞게 배포할 수 있어야 한다. 실제 관리형 Redis topology와 DB 제품의 호환성은 배포 시 별도 검증한다.

## 최소 재사용 묶음 관점의 재검토

이 표는 채택 이유와 다른 프로젝트에서의 검토 조건이다. 현재 확정한 기능을 임의로 제외하지 않으며 교체는 대안 문서와 변경 기록으로 관리한다.

| 항목 | 현재 상태 | 기본 묶음에 대한 평가 |
| --- | --- | --- |
| React·TypeScript / Java·Spring Boot·MyBatis | 확정 | 핵심 기반 유지 |
| Next.js | 확정 | 용도 재검토 가능. 라우팅·빌드·서버 렌더링 기능을 실제 사용할 범위를 정하고 범용성만으로 기능을 늘리지 않음 |
| TanStack Query | 확정 | 조회·검색·변경 후 갱신 예제에는 도움. 단순 요청만 있는 프로젝트에서는 과할 수 있으므로 실제 사용 예제로 가치를 확인 |
| React Hook Form·Zod | 확정 | 입력 화면이 반복되는 템플릿에 직접 도움. 간단한 폼만 있는 프로젝트에서는 생략 가능하나 현재 결정 유지 |
| Bean Validation | 확정 | 서버 입력 경계의 기본 검사로 유지 권장 |
| Redis·Spring Session Redis | 확정 | 다중 인스턴스 세션 요구로 유지. 별도 서비스 관리 비용이 있으나 사용자의 재사용 목표에 근거함. 일반 캐시 등으로 확대하지 않음 |
| Spring Security | 확정 | 인증·권한·CSRF의 표준 기반으로 유지 권장. Security Test와 별개 |
| Axios | 사용자 확정 | 브라우저 API 전송을 공통 인스턴스로 통일. Next 서버 fetch는 별도 서버 책임이며 브라우저 호출 경로와 중복하지 않음 |
| ESLint·Prettier / 기본 로깅 | 확정 | 반복 개발·코드 일관성에 직접 도움. 별도 로그 시스템 중복 금지 |
| springdoc·Swagger UI | 확정 | 브라우저 API 확인에 직접 도움. 개발 기능으로 한정하고 운영 비활성 |
| Flyway | 사용자 확정 | 로그인·기본 화면의 DB 기본 준비용으로 유지. 스키마와 로컬 합성 데이터를 분리하고 Docker 독립 실행 유지 |
| PostgreSQL | 확정 | 18.6을 로컬 예제 DB로 사용. 고객 DB 전체 호환을 보장하지 않음 |
| Docker Compose | 로컬 편의 방향 확정 | 로컬 DB·Redis 준비에 한정해 유지. Compose 자체 세부 구현은 미완료 |
| Gradle Wrapper / npm | 확정 | 정확 버전은 버전표 적용. 업무 기능 추가 라이브러리와 구분 |
| JUnit + Spring Boot Test | 사용자 확정 | 기본 테스트 실행·검증과 Spring 설정·Mapper·DB 연결 활용. 테스트용 접속 대상 분리 |
| Lombok | 사용자 확정 | 생성자·접근자 반복 코드 감소. 필요한 annotation만 사용 |
| JUnit·Spring Boot Test 외 선택 테스트·모니터링·기타 선택 도구 | 기본 미포함 | 문서에만 역할·도입 조건을 보존. 기본 설치·샘플·설정·CI에 끼워 넣지 않음 |

추가 자동화 도구를 기본 제외해도 빌드·타입·lint·실제 SQL·접근 제어·세션·보안 헤더 등 필요한 확인 자체를 생략하지 않는다. 검증하지 않은 항목은 미검증으로 기록한다.

## Redux를 사용하는 경우의 문서 전용 대안

현재 기본 선택은 Zustand다. Redux 대안 설명은 채택 변경·패키지 설치·두 구현 병행을 뜻하지 않는다.

- Redux 사용 시 공식 권장 Redux Toolkit(@reduxjs/toolkit) + React Redux(react-redux)를 사용한다.
- 현재 조합에서 최소 변경하려면 Zustand만 위 조합으로 교체하고 TanStack Query·Axios·React Hook Form·Zod를 유지한다. Redux는 공유 클라이언트 상태, TanStack Query는 서버 데이터의 소유자다. 조회 결과를 Redux에 복사하지 않는다.
- 처음부터 Redux 중심으로 선정하는 별도 프로젝트라면 Redux Toolkit + React Redux + RTK Query도 검토한다. RTK Query는 서버 조회·캐시 역할이므로 같은 API 데이터에 TanStack Query를 함께 적용하지 않는다.
- RTK Query의 기본 fetchBaseQuery를 선택하면 해당 브라우저 API 경로의 Axios는 필요 없다. 기존 Axios 정책을 재사용할 명확한 이유가 있으면 Axios 기반 baseQuery를 별도 설계하되 두 요청 경로를 병행하지 않는다.
- Next App Router에서는 사용자별 store가 서버 요청 간 공유되지 않도록 만들고 Client Component Provider 범위·초기 상태·화면 이동 시 초기화 정책을 정의한다. RSC가 store를 직접 읽거나 수정하도록 하지 않는다.
- Redux로 교체해도 서버 인증은 JWT와 공유 인증 상태 검증이 기준이다. 클라이언트 store의 로그인·역할 표시를 권한 판단 근거로 삼지 않는다.

근거: [Redux 권장 구성](https://redux.js.org/introduction/getting-started), [Next.js 구성](https://redux.js.org/usage/nextjs), [RTK Query](https://redux-toolkit.js.org/rtk-query/overview).

## Orval API 클라이언트 생성 계약

상태: Orval 기본 포함은 사용자 확정. 기본 버전 선정은 완료했으며 설치·생성 명령·실행 검증은 후속 구현 단계다. 타입 전용 openapi-typescript를 별도로 기본 설치하지 않는다.

### 생성 범위와 책임

- Spring Controller·DTO의 API 계약 → springdoc OpenAPI 명세 → Orval → TypeScript 요청/응답 타입·Axios 호출 함수·TanStack Query 조회/변경 Hook 흐름을 사용한다.
- 브라우저 호출용 생성 코드만 기본 대상으로 한다. Next Server Component의 서버 fetch 계약에 브라우저 Hook을 연결하지 않는다. Zustand·React Hook Form은 기존 역할을 유지한다.
- 입력 명세는 버전 관리하는 로컬 JSON/YAML을 기준으로 재생성 가능하게 한다. 운영 서버 접근이나 운영 OpenAPI 공개가 프론트 빌드·타입 생성의 전제조건이 되지 않도록 한다.
- API 경로·메서드·고유하고 안정적인 operationId·요청/응답 DTO·필수/null·상태 코드·오류 schema·페이지 형식을 명세한다. Java Long과 JavaScript number의 정밀도 등 표현 차이는 API 계약에서 정하며 생성기가 자동 해결한다고 가정하지 않는다.
- 기본 생성은 타입·요청 함수·Query Hook에 한정한다. mock/MSW·테스트·추가 validation client 생성은 기본 비활성이다. 화면·폼·업무 정책은 생성 대상이 아니다.
- 기존 Zod 폼 검증과 생성 API 타입의 역할을 구분한다. 생성 TypeScript 타입만으로 실제 응답의 런타임 검증을 수행했다고 판단하지 않는다.

### 공통 Axios 연결

- Orval custom mutator를 통해 기존 공통 Axios 요청 함수에 연결한다. generated 코드가 별도 Axios 인스턴스나 하드코딩한 운영 주소를 만들지 않도록 한다.
- 쿠키·CSRF token·timeout·취소 signal·안전한 오류 변환은 공통 요청 경계가 소유한다. 조회와 변경 Hook에 취소/오류가 올바르게 전달되는지 확인한다.
- 같은 출처 /api 기준으로 명세의 경로와 Axios baseURL을 맞춰 /api 중복 접두사·외부 목적지 호출을 방지한다.
- 401·403·검증 오류·CSRF·네트워크 오류가 생성 Hook에서도 기존 계약으로 전달되어야 한다. 자동 알림·로그인 반복·변경 요청 재전송을 생성 경로에 추가하지 않는다.
- 생성 Query key/옵션을 검토하고 조회 캐시와 mutation 성공 후 필요한 무효화 정책은 별도로 작성한다. operationId만으로 업무상 관련 목록을 자동 갱신한다고 가정하지 않는다.
- logout·계정 전환 시 기존 요청 취소와 개인 Query 캐시 정리를 유지한다. 서버 전역 인스턴스에 사용자 쿠키·CSRF·개인 상태를 보관하지 않는다.

### 생성 파일과 갱신 절차

- 생성 폴더와 직접 작성하는 공통 요청 함수·화면/업무 코드를 분리한다. 생성 파일은 직접 수정하지 않고 명세·Orval 설정·공통 요청 경계를 수정한 뒤 재생성한다.
- 명세 export·생성은 명시적 개발 명령으로 제공하고 도구 버전·lock을 고정한다. 정확한 명령과 경로는 구현 단계에서 확정한다. 매 화면 접근이나 production 시작 때 코드를 생성하지 않는다.
- API 변경 → 명세 갱신 → 생성 → 변경 diff 확인 → 타입 검사·빌드·대표 API 동작 확인 순서로 검증한다. 동일 입력·버전에서 재생성 결과가 일치하는지 확인한다.
- 기본안은 비밀 없는 명세와 생성 결과를 저장소에 포함해 프론트만 작업할 때도 backend 실행 없이 시작하게 하는 것이다. 내부 운영 URL·인증 토큰·실제 응답/개인정보를 명세·예제·생성 결과에 넣지 않는다.
- 도구 채택이 공개 레지스트리 배포나 새 CI job 생성을 뜻하지 않는다. 새 자동화는 필요 범위를 별도로 정한다.
- 선정 버전은 확정 버전표를 적용하고 설치 시 lockfile과 실제 생성 결과의 타입 호환성을 확인한다.

근거: [Orval 개요](https://orval.dev/docs/), [TanStack Query Hook 생성](https://orval.dev/docs/guides/react-query/), [공통 Axios 연결](https://orval.dev/docs/guides/custom-axios/).

### 다른 생성 도구 — 문서 전용 대안

| 도구 | 생성 범위 | 검토할 상황 |
| --- | --- | --- |
| OpenAPI Generator / typescript-axios | TypeScript 모델·Axios API 클라이언트 | 고객이 표준 생성 도구로 사용하거나 여러 언어 SDK를 같은 체계로 생성할 때 |
| swagger-typescript-api | TypeScript 타입·Axios 또는 fetch 클라이언트 | Query Hook 생성 없이 TypeScript API 호출 코드 중심으로 생성할 때 |
| openapi-typescript | TypeScript 타입 | API 호출·Hook은 직접 작성하고 타입만 명세와 동기화하려 할 때 |

위 도구는 설치·설정·샘플을 기본으로 추가하지 않는다. Orval을 교체할 때 하나를 선정하고 동일 API의 타입·호출 코드를 여러 생성기가 중복 소유하지 않게 한다.

근거: [typescript-axios](https://openapi-generator.tech/docs/generators/typescript-axios/), [swagger-typescript-api](https://github.com/acacode/swagger-typescript-api), [openapi-typescript](https://openapi-ts.dev/introduction).

## 공통 컨트롤과 HTML·CSS 퍼블리싱 적용

- 목표: 자주 쓰는 컨트롤의 기본 동작을 공통 구현하고, 프로젝트별 디자인·옵션을 조정해 간편하게 재사용한다. HTML·CSS 퍼블리싱 적용을 기준으로 하며 별도 CSS 작성 도구를 필수로 두지 않는다.
- 도구: Radix Primitives·AG Grid Community·Apache ECharts 기본 포함은 사용자 확정이다. Radix Themes·Tailwind·shadcn/ui는 기본 포함하지 않는다. 설치·실행 검증은 아직 수행하지 않았다.
- 그리드·차트 목표: AG Grid Community와 Apache ECharts를 공통 컨트롤로 제공하고 기본 디자인·옵션을 프로젝트별로 조정한다. 지금은 목표·도구 선택만 기록하며 상세 설계는 해당 작업 시 진행한다.
- 라이선스: AG Grid Community는 MIT 범위만 사용하고 Enterprise 의존성·기능·평가판을 포함하지 않는다. ECharts는 Apache 2.0이며 배포 시 사용 패키지의 LICENSE·NOTICE 등 적용 고지를 유지한다.
- 디자인 변경 목표: 공통 컨트롤 동작과 외형을 분리해 기본 디자인으로 사용한 뒤 퍼블리싱에 맞춰 조정할 수 있게 한다. 상세 스타일 분리 방식은 추후 설계한다.
- 컨트롤 목록·props·기본값·스타일 구조·구현/검증 방식의 상세 설계는 해당 작업 시 진행한다. [HDR07](security/headers-csp.md)의 UI/CSP 통합 조건을 적용하고 구현 전 선택 컴포넌트의 Next.js·React·CSP 동작을 검증한다.

근거: [Radix Primitives](https://www.radix-ui.com/primitives/docs/overview/introduction), [일반 CSS 적용](https://www.radix-ui.com/primitives/docs/guides/styling), [AG Grid 무료·유료 범위](https://www.ag-grid.com/react-data-grid/community-vs-enterprise/), [AG Grid 패키지 라이선스](https://github.com/ag-grid/ag-grid/blob/latest/LICENSE.txt), [ECharts](https://echarts.apache.org/en/index.html), [ECharts 라이선스](https://github.com/apache/echarts/blob/master/LICENSE).
