# 상황별 대체 조합

기준일: 2026-10-26 / 상태: 문서 전용 대안. 기본 설치·구현 대상이 아니다.

현재 채택은 [확정 구성·버전표](stack-versions.md)다. 아래의 “유리한 상황”은 공식 기능 설명을 바탕으로 한 프로젝트 설계 판단이며 사용량 순위나 모든 고객에게 최적이라는 뜻이 아니다. 대안은 실제 요구가 생길 때 교체 단위를 판단하기 위한 기록이다. 대체 제품의 정확 버전·라이선스·지원 기간은 도입 시 다시 확인한다.

## 1. 프로젝트 상황에 맞는 전체 조합

| 상황 | 적합한 대체 조합 | 현재 구성에서 달라지는 점·주의 |
| --- | --- | --- |
| 사내 업무 화면 위주, SEO·서버 렌더링 불필요 | Vite + React + TypeScript + 기존 Query/Axios/Zustand/폼 + Spring MVC/MyBatis | Next만 교체. 정적 파일 배포가 간편해질 수 있으나 라우팅·인증 진입·환경변수·CSP를 새로 정함 |
| 공개 서비스, 검색 노출·서버 렌더링 중요 | 현재 Next App Router + Spring API 유지 | 서버/클라이언트 데이터 조회 소유권과 사용자별 캐시 분리 필요 |
| 고객이 Redux를 표준으로 사용 | Next 또는 Vite + Redux Toolkit + React Redux + TanStack Query + Axios | Zustand만 교체하는 최소 변경. 서버 캐시는 Query가 계속 소유 |
| Redux 기반으로 서버 데이터까지 통합 | Redux Toolkit + React Redux + RTK Query | Zustand·TanStack Query 역할을 대체. 기본 fetchBaseQuery 사용 시 Axios도 제거 검토. Orval 출력 방식을 다시 선정 |
| 고객 표준이 JPA, CRUD·엔티티 중심 | Spring Data JPA + 필요 시 JPA Querydsl + PostgreSQL/MySQL | MyBatis 교체. 엔티티·영속성·flush·연관관계·N+1·페이징 기준 새로 설계 |
| SQL 자산·복잡한 조회·기존 Mapper가 많음 | 현재 Spring MVC + MyBatis 단독 유지 | 고객 DB driver·dialect·migration으로 변경. JPA를 편의만으로 추가하지 않음 |
| Redis를 별도로 운영하기 어려움 | Spring Security + Spring Session JDBC + 업무 RDB | Redis 공유 세션을 JDBC로 교체. 다중 서버 세션 유지 가능하나 세션 부하·만료 정리·테이블 설계 필요 |
| 고객 DB·Redis가 이미 제공됨 | 현재 앱 조합 + 고객 제공 외부 서비스 | 로컬 Compose만 대체. 설정·TLS·계정·네트워크와 실제 버전 호환성 확인 |
| 고객 UI 디자인보다 빠른 업무 화면 조립 중요 | MUI 또는 Ant Design + 기존 React/폼/API | Radix 기반 공통 컨트롤을 완성형 UI로 교체 검토. 퍼블리싱 적용·기본 테마·폼 동작을 통일 |
| 고객 플랫폼이 Kubernetes·관리형 클라우드 | 현재 앱 + 외부 DB/세션 + 플랫폼별 배포 설정 | Docker Compose를 운영 배포 방식으로 전제하지 않음. TLS·secret·probe·배포·로그 수집은 환경에 맞춤 |

[Vite](https://vite.dev/guide/), [Redux Toolkit](https://redux-toolkit.js.org/introduction/getting-started), [RTK Query](https://redux-toolkit.js.org/rtk-query/overview), [Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/), [Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html), [MUI](https://mui.com/material-ui/getting-started/), [Ant Design](https://ant.design/docs/react/introduce/).

## 2. 프론트 상태·입력·통신

| 현재 기본 | 대안 | 유리한 상황 | 교체 시 확인 |
| --- | --- | --- | --- |
| TanStack Query | SWR | 단순 조회·재검증 중심이고 팀이 SWR 사용에 익숙함 | mutation·페이지·취소·오류·캐시 정책과 Orval 출력 변경 |
| TanStack Query | Next 서버 fetch 중심 | 서버에서 대부분 조회하고 클라이언트 캐시 요구가 작음 | 개인 응답의 공유 캐시 금지. 인터랙티브 화면의 갱신 요구는 별도 검토 |
| Axios | 브라우저 fetch + 공통 함수 | 외부 HTTP 라이브러리를 줄이고 표준 API면 충분함 | timeout/취소·HTTP 오류·JSON·CSRF·쿠키를 공통 처리하고 Orval 전송 출력 교체 |
| Zustand | Redux Toolkit + React Redux | 고객 표준·액션 이력·미들웨어·팀 운영 규칙 중요 | Query를 유지할지 RTK Query로 함께 교체할지 먼저 결정 |
| Zustand | Context + useReducer | 낮은 빈도로 바뀌는 소규모 공유 상태 | 렌더링 범위·Provider 위치. 서버 데이터 캐시를 직접 다시 구현하지 않음 |
| React Hook Form + Zod | 단순 controlled input + 서버 검증 | 필드가 적고 복잡한 폼 상태 관리가 불필요한 작은 화면 | 접근성·오류 표시·중복 제출·서버 오류 연결은 유지 |
| Zod | Valibot 등 다른 runtime schema | 클라이언트 검증 번들 크기가 실제 병목이거나 고객 표준 | resolvers·입출력 변환·오류 코드·생성 타입 경계를 함께 교체 |

서버 데이터, 공유 UI 상태, 폼 상태는 다른 책임이다. 도구를 바꿔도 같은 API 응답을 여러 저장소에 복제하지 않는다. Redux Toolkit을 쓰더라도 RTK Query까지 반드시 도입하는 것은 아니다.

[SWR](https://swr.vercel.app/), [Next fetch](https://nextjs.org/docs/app/api-reference/functions/fetch), [Fetch](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API), [React Context](https://react.dev/learn/passing-data-deeply-with-context), [Valibot](https://valibot.dev/).

## 3. OpenAPI 클라이언트·문서

| 대안 | 유리한 상황 | 현재 Orval·springdoc과의 차이 |
| --- | --- | --- |
| OpenAPI Generator typescript-axios | 여러 언어 SDK를 같은 명세에서 만들고 서비스 클래스/함수 구조가 필요 | Axios 클라이언트 생성 중심. 현재 Query Hook 통합·공통 요청 경계는 별도 연결 |
| swagger-typescript-api | TypeScript 타입과 서비스 호출 코드를 원하는 형태로 생성 | 템플릿·옵션 조정 가능. Query Hook 생성·운영 방식은 별도 비교 |
| openapi-typescript + 필요 시 openapi-fetch | 타입 중심으로 시작하고 클라이언트 계층은 작게 직접 관리 | openapi-typescript 자체는 타입 생성. 호출 함수·Query Hook까지 자동 제공한다고 가정하지 않음 |
| 명세 우선 OpenAPI + 서버/클라이언트 생성 | 여러 팀이 API 계약을 먼저 합의 | Spring annotation에서 추출하는 현재 방향과 명세의 원본이 달라짐. 양방향 수작업 동기화 금지 |
| Swagger UI 대신 다른 spec viewer | 문서 UX 또는 사내 표준 도구가 중요 | spec은 유지 가능. 추가 viewer 라이선스·CSP·운영 노출 경로 검토 |

생성기는 프로젝트별 하나를 원본 경로로 사용한다. 기존 생성 결과를 수작업으로 고친 뒤 다른 생성기를 겹쳐 적용하지 않는다. 요청 취소·오류 타입·쿠키/CSRF·날짜/숫자/null 처리와 생성 결과 타입 검사는 공통 교체 기준이다.

[OpenAPI Generator](https://openapi-generator.tech/docs/generators/typescript-axios/), [swagger-typescript-api](https://github.com/acacode/swagger-typescript-api), [openapi-typescript](https://openapi-ts.dev/introduction), [openapi-fetch](https://openapi-ts.dev/openapi-fetch/), [springdoc](https://springdoc.org/).

## 4. UI·스타일·그리드·차트

| 현재 기본 | 대안 | 유리한 상황·대가 |
| --- | --- | --- |
| Radix Primitives + 직접 CSS | React Aria Components | 디자인 직접 소유를 유지하면서 국제화·복잡한 접근성 요구를 비교할 때. 공통 컨트롤 계약과 스타일을 이식 |
| Radix Primitives + 직접 CSS | MUI / Ant Design | 디자인 시스템을 처음부터 만들기보다 완성된 업무 컨트롤 조립을 우선할 때. 기존 HTML/CSS와 테마 구조의 차이 검토 |
| Radix Primitives | Radix Themes | Radix의 완성 스타일·토큰을 함께 채택할 때. Primitives 위 CSS만 얹었다 제거하는 방식으로 취급하지 않음 |
| 직접 CSS·CSS Modules | Tailwind CSS / shadcn/ui | 팀이 utility class·복사 소스 기반 컴포넌트 운영을 원할 때. 현재 퍼블리싱 중심 작업에 필수는 아님 |
| CSS Modules | Sass | 믹스인·함수·기존 SCSS 자산이 실제 필요할 때. 별도 컴파일 의존성·규칙 추가 |
| AG Grid Community | TanStack Table + 필요한 가상화 | 테이블 HTML·디자인·동작을 직접 세밀하게 소유하려 할 때. 완성 그리드가 아니므로 편집·키보드·가상화 통합 작업 증가 |
| AG Grid Community | AG Grid Enterprise | pivot·고급 grouping·Excel export 등 유료 기능이 실제 요구될 때. 라이선스·배포 범위 확인 후 명시적으로 변경 |
| ECharts | Recharts | React 컴포넌트·SVG 기반의 비교적 일반적인 차트를 화면과 조립하고 싶을 때 |
| ECharts | Chart.js | 표준적인 canvas 차트 중심이고 차트 요구가 단순할 때. React 연결은 직접 또는 별도 wrapper 검토 |

AG Grid Community의 서버 페이징은 일반 API로 구현할 수 있지만 Enterprise의 Server-Side Row Model과 같은 기능이라고 설명하지 않는다. 차트 라이브러리 교체 시 옵션·이벤트·formatter를 그대로 호환된다고 가정하지 않는다.

공통 컨트롤은 필요한 동작·스타일을 통일해 간편하게 재사용하는 것이 목표다. 위 선택지 때문에 여러 UI·그리드·차트 라이브러리를 동시에 설치하지 않는다. 구체 컨트롤 목록·공통 props·디자인·접근성·CSP는 후속 작업에서 정한다.

[React Aria](https://react-aria.adobe.com/), [Radix Themes](https://www.radix-ui.com/themes/docs/overview/getting-started), [Tailwind](https://tailwindcss.com/docs/installation/using-vite), [shadcn/ui](https://ui.shadcn.com/docs), [Sass](https://sass-lang.com/guide/), [TanStack Table](https://github.com/TanStack/table), [AG Grid 구분](https://www.ag-grid.com/react-data-grid/community-vs-enterprise/), [Recharts](https://github.com/recharts/recharts), [Chart.js](https://www.chartjs.org/docs/latest/).

## 5. 백엔드·데이터 접근·DB

| 현재 기본 | 대안 | 유리한 상황 | 함께 바꿀 부분 |
| --- | --- | --- | --- |
| OpenJDK Eclipse Temurin 25 | Microsoft Build / Corretto / Zulu 등 Java 25 | 고객 인증 배포판·기술 지원·OS/CPU 지원이 우선 | 보안 패치·인증서·timezone·JVM 옵션·빌드 JVM 확인 |
| Java 25 | Java 21 LTS + 호환 Boot | 고객 agent·WAS·보안 모듈이 25 미지원 | 컴파일 target·언어 API·플러그인·고객 제품 인증 범위 |
| Boot 4.1 | Boot 4.0 또는 지원 중 Boot 3.5 계열 | 고객 공통 모듈·외부 라이브러리가 Boot 4.1 미지원 | Security·MyBatis starter·springdoc·Jackson·Servlet을 계열별로 함께 변경. 지원 종료/유료 지원 여부 확인 |
| Spring MVC | WebFlux + reactive driver | 대량 동시 I/O·streaming 요구와 팀의 reactive 운용 역량이 있음 | blocking MyBatis를 그대로 event loop에서 쓰지 않음. 세션·보안·데이터 접근까지 별도 설계 |
| MyBatis | Spring Data JPA | CRUD·엔티티 생명주기·도메인 모델 중심 | 영속성 컨텍스트·flush·연관관계·SQL 확인·트랜잭션 재설계 |
| MyBatis | JPA + JPA Querydsl | JPA 기반에서 복잡한 조건을 타입 안전 Java 쿼리로 조합 | 유지보수되는 배포판·Jakarta/Boot/JDK 지원·Q타입 생성 확인. SQL을 직접 실행하는 MyBatis와 동작 방식 다름 |
| MyBatis | JPA + MyBatis | 고객이 혼합 표준을 요구하고 ORM CRUD와 기존 SQL을 유지해야 함 | 같은 DataSource·transaction manager·연결 공유 외에도 JPA flush·캐시 동기화 필요. 단순 편의용 기본 조합으로 추가하지 않음 |
| MyBatis | Spring JDBC / JdbcClient | SQL 수가 적고 Mapper 프레임워크가 불필요 | SQL·행 매핑·페이징 구조를 직접 정함 |
| PostgreSQL | MySQL / MariaDB | 고객 기존 DB·운영 역량이 이 계열에 있음 | 동일 DB로 로컬/검증 정렬. driver·Flyway 모듈·SQL·시간/문자·DDL·임시 테이블 재검증 |
| PostgreSQL | SQL Server / Oracle | 고객 표준·기존 프로시저·관리 도구 연동이 필수 | 제품·driver·기능별 라이선스/지원, SQL 방언·권한·DDL·temp table 동작 검증 |
| PostgreSQL | H2 | DB 차이에 영향 없는 빠른 단위성 검증 | 실제 SQL·잠금·임시 테이블·운영 DB 호환 검사 대체 금지 |
| Flyway | Liquibase | 조직이 changeset·다양한 changelog 형식과 기존 승인 체계를 사용 | schema 소유 도구를 하나로 교체. 기존 이력 baseline·checksum·rollback 지원 범위를 설계 |

DB는 로컬 구성 편의 때문에 PostgreSQL을 선택했으며 모든 RDB의 동작을 추상화하는 목표는 아니다. 고객 DB가 정해지면 그 DB의 로컬/격리 검증 환경으로 바꾸는 것이 SQL 호환성을 확보하는 경로다. 임시 테이블은 같은 “트랜잭션” 이름만으로 수명을 보장하지 않으며 연결·DB 제품별 규칙을 확인한다.

[Temurin](https://adoptium.net/temurin/releases/), [Corretto](https://aws.amazon.com/corretto/), [Zulu](https://www.azul.com/downloads/), [Boot 지원](https://spring.io/projects/spring-boot#support), [MyBatis 호환표](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/), [Spring JDBC](https://docs.spring.io/spring-framework/reference/data-access/jdbc.html), [JPA](https://docs.spring.io/spring-data/jpa/reference/), [WebFlux](https://docs.spring.io/spring-framework/reference/web/webflux.html), [Liquibase](https://www.liquibase.com/community).

## 6. 공유 세션·SSO·운영

| 현재 기본 | 대안·추가 | 유리한 상황·확인할 조건 |
| --- | --- | --- |
| Redis 공유 세션 | Valkey + Spring Session Redis 접근 경로 검증 | 고객이 Valkey·허용적 라이선스 계열을 표준화할 때. Redis 8의 모든 기능/파일을 그대로 호환한다고 가정하지 않고 기본 세션 명령·알림·TTL·HA·보안 설정 검사 |
| Redis 공유 세션 | Spring Session JDBC | 별도 Redis 운영이 어렵고 RDB는 제공될 때. 만료 정리·동시성·DB 부하·다중 서버 시험 필요 |
| 직접 운영 Redis | 관리형 Redis/호환 서비스 | 운영 패치·HA·백업 부담을 줄일 때. TLS·인증·명령 제한·pub/sub·topology·eviction·serializer 검증 |
| 향후 일반 로그인 | OIDC 로그인 + 기존 내부 사용자/권한·공유 세션 | IdP가 OIDC 제공. 외부 로그인 결과를 내부 권한에 매핑하며 로컬 세션·logout 연동 유지 |
| 향후 일반 로그인 | SAML 로그인 + 기존 공유 세션 | 고객 IdP가 SAML 표준. AuthnRequest 상태 공유·서명/인증서·POST callback 쿠키·프록시 URL 검증 |
| 향후 일반 로그인 | Windows 통합 인증/고객 agent 연계 + 내부 세션 | 고객 도메인·보안 agent 표준. 실제 지원 JDK·OS·서명·신뢰 프록시·신원 헤더 위조 방지 확인 |
| 브라우저 세션 API | OAuth2 Resource Server + Bearer token | 모바일·외부 API·서비스간 호출 표준이 요구될 때. 발급자·audience·만료·키회전·폐기·브라우저 저장/CSRF까지 별도 설계 |
| 운영 모니터링 기본 제외 | Actuator + Micrometer + 기존 수집 플랫폼 또는 Prometheus/Grafana | 앱 지표·여러 인스턴스 통합 화면이 실제 필요할 때. instance별 endpoint를 수집기가 읽고 UI가 집계. 관리망·접근 통제와 운영별 연결 설정 필요 |
| 기본 로깅·운영 JSON 출력 | 기존 중앙 로그 수집기/APM 연결 | 고객 운영 수집기에 맞춰 trace·검색·감사를 연결할 때. 플랫폼·수집 경로·민감값 필터·보관 정책을 맞춤. JSON 출력 자체는 기본 Boot 로깅 기능이며 별도 도구 도입이 아님 |

OIDC·SAML은 인증 프로토콜이며 세션 저장소를 대체하는 도구가 아니다. 공유 세션을 준비했다고 모든 provider 연동이 자동 완성되지는 않는다. 모든 인증 SDK·agent·IdP·JWT 발급 서버를 기본 프로젝트에 설치하지 않는다.

[Valkey 이전 범위](https://valkey.io/topics/migration/), [Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html), [OIDC/OAuth 로그인](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/index.html), [SAML](https://docs.spring.io/spring-security/reference/servlet/saml2/login/overview.html), [Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html), [Actuator](https://docs.spring.io/spring-boot/reference/actuator/index.html), [Prometheus 수집 구성](https://prometheus.io/docs/prometheus/latest/configuration/configuration/).

## 7. 개발·테스트 도구

아래는 전부 문서 전용 선택사항이며 기본 의존성·샘플·설정·CI를 추가하지 않는다. 현재 기본 테스트는 JUnit + Spring Boot Test다.

| 도구·대안 | 유리한 상황 | 적용 범위·주의 |
| --- | --- | --- |
| AssertJ | 목록·객체·예외 검증 표현을 편하게 작성할 필요 | JUnit Assertions로 부족한 실제 사례가 생길 때 |
| Spring Security Test | 직접 작성한 권한·소유권 로직의 반복 회귀 확인 | mock 사용자 테스트는 실제 로그인·다중 서버 세션 검사를 대신하지 않음 |
| Testcontainers | 테스트용 PostgreSQL·Redis 생성/정리를 자동화 | CI 실행 환경 비용·Docker 필요. 격리된 외부 테스트 서비스 경로도 유지 |
| Vitest + React Testing Library | 프론트 로직·입력·오류 표시의 반복 회귀 | 컴포넌트 동작에 집중하며 라이브러리 내부 구현 복제 시험 금지 |
| Playwright | 실제 브라우저 로그인·라우팅 흐름 자동 회귀의 이득이 명확 | 별도 검증 환경에서 브라우저 실행. 업무 운영 서버 필수 설치 도구가 아님. 현재 기본 제외 |
| MapStruct | 반복적인 DTO 변환이 많아짐 | annotation processor·매핑 누락 정책 설계. 소량 매핑에는 직접 작성 |
| Gradle 의존성 checksum/PGP 검증 | 고객의 공급망 정책이 의존성별 검증·승인 이력을 요구 | 문서 전용. verification-metadata.xml은 기본 미사용. IDE sources까지 검증 범위를 정하고 갱신 비용·최초 신뢰 한계 고려 |
| 패키지 버전별 설치 승인·게시 경과 시간 예외 | 조직이 별도 공급망 강화 정책을 요구 | 문서 전용. 기본은 버전별 수동 대장 제외, 필요한 설치 스크립트와 자동 lockfile integrity는 별개로 관리 |
| Maven Wrapper | 고객 표준이 Maven이거나 기존 parent/POM 체계 사용 | Gradle 대체. BOM·plugin·Java target·검사·잠금/무결성 기준 유지 |
| pnpm / Yarn | 고객 표준·기존 lockfile·CI 또는 대규모 workspace에 맞춤 | 기본 npm을 대체. 여러 lockfile을 병행 관리하지 않음 |
| Docker 없는 외부 DB/Redis | Docker 설치 정책이 제한되거나 외부 서비스가 준비됨 | 동일 접속·자격·TLS 계약 사용. 앱 코드 변경보다 환경 설정 교체 |

[JUnit](https://docs.junit.org/current/user-guide/), [Spring 테스트](https://docs.spring.io/spring-boot/reference/testing/), [AssertJ](https://assertj.github.io/doc/), [Security Test](https://docs.spring.io/spring-security/reference/servlet/test/index.html), [Testcontainers](https://java.testcontainers.org/), [Vitest](https://vitest.dev/guide/), [Testing Library](https://testing-library.com/docs/react-testing-library/intro/), [Playwright](https://playwright.dev/docs/intro), [MapStruct](https://mapstruct.org/documentation/), [Maven Wrapper](https://maven.apache.org/wrapper/).

## 8. 대안 적용 원칙

1. 고객 요구·기존 자산·운영 제약 중 교체 이유를 기록하고 같은 역할의 기본 도구를 제거/대체할 범위를 정한다.
2. 의존성 이름만 바꾸지 않고 생성 코드·설정·보안·마이그레이션·검증 문서를 함께 갱신한다.
3. 교체 목적과 관계없는 스택까지 함께 바꾸지 않는다. 시험만을 위해 여러 대안을 기본 설치하지 않는다.
4. 기본 설정 계약은 단순하게 유지한다. 모든 제품을 수용하는 거대한 플러그인 프레임워크나 미사용 추상 계층을 선구현하지 않는다.
5. 현재 버전표는 기본 조합의 선정 기록이다. 대체 조합은 도입 시 정확 버전과 실제 실행 증거를 새로 기록한다.
