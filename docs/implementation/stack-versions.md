# 확정 기술 구성과 버전 기준

기준일: 2026-10-26 (Asia/Seoul) / 상태: 구성·버전 선정 및 1차 기본 설치·빌드·로컬 기초 연동 검증 완료. 세부 증거와 후속 범위는 change-history.md 및 모듈 README 참조.

사용자가 DB 제품과 호환 가능한 최신 안정 버전 선정을 설계자에게 위임한 결정 기록이다. 이 문서의 기본 조합을 채택한다. [기술별 역할과 통합 원칙](technology-stack.md), [상황별 대체 조합](stack-alternatives.md)을 함께 참조한다. 예제 기능·공통 컨트롤 상세·배포별 실행 설정은 후속 설계다.

## 1. 전체 기본 조합

| 영역 | 확정 구성 | 역할과 선정 이유 |
| --- | --- | --- |
| 저장소 | 프론트·백엔드 모노레포 | 기본 설정·문서·API 계약을 함께 참조하고 변경 |
| 프론트 기반 | Next.js App Router + React + TypeScript strict | 화면·라우팅·렌더링과 타입 검사 |
| 서버 데이터 | TanStack Query + Axios | 조회 캐시·로딩·갱신 상태와 브라우저 HTTP 전송을 분담 |
| API 생성 | Orval | springdoc OpenAPI에서 타입·Axios 함수·TanStack Query Hook 생성 |
| UI 공유 상태 | Zustand | 화면 간 클라이언트 상태 공유. API 결과·폼 상태와 중복 저장 금지 |
| 입력 | React Hook Form + Zod + resolvers | 입력 상태·제출 처리와 런타임 유효성 검증 |
| UI 기반 | Radix Primitives + 일반 CSS·CSS Modules·CSS 변수 | 동작·접근성 기반을 활용하고 프로젝트 디자인 직접 적용 |
| 그리드·차트 | AG Grid Community + Apache ECharts | 재사용할 공통 그리드·차트 기반. Enterprise 미포함 |
| Java API | OpenJDK 25 LTS + Spring Boot + Spring MVC | Java 최신 LTS와 Servlet 기반 REST API |
| 인증 기반 | Spring Security + Spring Session Data Redis | 쿠키·CSRF·권한·Redis 공유 세션. 인증 수단 추가 시 기반 유지 |
| 데이터 접근 | MyBatis + Spring JDBC + HikariCP | SQL을 직접 관리하고 서비스 어노테이션으로 트랜잭션 경계 설정 |
| DB·스키마 | PostgreSQL + Flyway | 로컬 준비가 쉬운 RDB와 버전별 테이블 생성·변경 SQL |
| 서버 입력·코드 보조 | Bean Validation + Lombok | 서버 입력 검증과 생성자·접근자 반복 코드 감소 |
| 로그 | Boot 기본 SLF4J + Logback | 기존 로깅 기반 재사용, 운영 UTC JSON 출력·비밀값/개인정보 정제. 별도 중앙 수집기·APM 기본 미설치 |
| 기본 검사 | TypeScript·ESLint·Prettier / JUnit·Spring Boot Test | 타입·정적 검사·형식과 기본 Java/Spring 테스트 |
| 실행·빌드 | Node.js LTS + npm / Gradle Wrapper | 프로젝트별 실행·설치·빌드 버전 고정 |
| 로컬 서비스 준비 | Docker Compose로 PostgreSQL·Redis | Docker는 로컬 편의 도구. 제품 런타임은 외부 서비스 접속 설정 사용 |

기준 흐름은 브라우저 → 단일 HTTPS 출처 → Next 화면 또는 Spring /api/** → PostgreSQL·Redis다. Next 서버와 Spring API의 책임을 분리하며 인증·업무·DB 처리는 Spring이 소유한다. 운영 프록시 제품과 도메인은 배포 설계에서 정한다.

## 2. 런타임·도구·로컬 서비스 버전

| 대상 | 채택 버전 | 관리 방식·근거 |
| --- | --- | --- |
| OpenJDK 배포판 | Eclipse Temurin 25 LTS, HotSpot | Java 25 사용. OS별 설치 가능한 정식 패치 버전을 확인하여 기록. JDK 설치물은 저장소에 포함하지 않음 |
| Java 컴파일·실행 기준 | 25 | preview 기능 미사용. IDE·Gradle JVM·toolchain·실행 JVM 기준 일치 |
| Node.js | 24.22.1 LTS | Next·Orval 실행 기준. 운영 Node도 동일 계열과 패치 기준 적용 |
| npm | 12.2.0 | packageManager·engines에 기록, package-lock.json 단일 사용, 설치 재현은 npm ci |
| Gradle Wrapper | 9.8.2 | Java 25 실행·toolchain 지원, Boot 4.1의 Gradle 9 지원 범위 |
| Spring Boot 플러그인·BOM | 4.1.2 | Gradle native BOM 사용. 추가 dependency-management 플러그인은 기본 불필요 |
| PostgreSQL | 18.6 | 공식 postgres:18.6 이미지. 현재 안정판 18, 19는 RC이므로 제외 |
| Redis Open Source | 8.10.2 | 공식 redis:8.10.2 이미지. 기본 용도는 공유 세션 |
| Docker Engine | 29.9.2 참고 기준 | 로컬 준비 검증 목표. OS별 Docker Desktop이 제공하는 Engine은 실제 구현 때 기록 |
| Docker Compose 플러그인 | 5.6.1 참고 기준 | docker compose 명령 기반. 구형 독립 docker-compose 명령 전제 금지 |

Docker Engine·Compose는 애플리케이션 의존성이 아니다. 설치 가능한 Docker Desktop 버전과 지원 OS는 실제 로컬 준비 단계에서 확인한다. Docker 없는 서버에서도 JAR/Node와 외부 PostgreSQL·Redis로 실행해야 한다.

[Temurin 배포본](https://adoptium.net/temurin/releases/?version=25), [Temurin 지원 정책](https://adoptium.net/support/), [Node 24.22.1](https://nodejs.org/en/blog/release/v24.22.1), [Node 지원 일정](https://github.com/nodejs/Release), [Gradle 릴리스](https://gradle.org/releases/), [Gradle Java 호환표](https://docs.gradle.org/current/userguide/compatibility.html), [Boot 요구사항](https://docs.spring.io/spring-boot/system-requirements.html), [PostgreSQL 지원표](https://www.postgresql.org/support/versioning/), [Redis 릴리스](https://github.com/redis/redis/releases/tag/8.10.2), [Engine 릴리스](https://github.com/moby/moby/releases), [Compose 릴리스](https://github.com/docker/compose/releases/tag/v5.6.1).

### Java 배포판과 LTS 판단

Java 25 LTS를 사용하며, 사용자 결정으로 범용성과 사용 저변을 우선하여 Eclipse Temurin(HotSpot)을 기본 배포판으로 선택한다. 이전 Microsoft Build 선택을 대체한다. 확인한 공식 배포 목록에는 Temurin 25.0.5+8 릴리스와 Windows x64 25.0.4.1+1 안내가 함께 존재하므로 전체 OS가 동일 패치를 제공한다고 간주하지 않는다. 설치 시 OS/아키텍처별 정식 JDK와 보안 패치 제공 상태를 확인하고 실제 버전을 기록한다. Windows x64 Temurin 25.0.4.1+1에서 테스트 15개와 JAR 생성이 통과했다. 실제 실행 기록은 backend/README.md에 있으며 기존 Microsoft JDK 검증 결과와 구분한다. 고객 표준·지원 계약에 따라 Microsoft Build·Corretto·Zulu 등 같은 Java 25 배포판을 대안으로 검토한다. 배포판별 build 번호는 서로 직접 대응하지 않는다.

Node 26은 기준일 Current이고 LTS 전환 예정일이 2026-10-28이므로 이번 기준은 24 LTS다. Node 24 지원 종료 예정은 2028-04-30, PostgreSQL 18 지원 종료 예정은 2030-11-14다. Temurin의 패치 제공 및 지원 일정은 공식 지원 정책을 기준으로 재확인한다. JDK 무료 업데이트와 유료 지원 계약은 구분한다. Boot·도구는 해당 유지보수 계열의 지원 정책을 업데이트 시 다시 확인하며 고정 버전을 영구 지원 버전으로 취급하지 않는다.

## 3. 프론트 직접 의존성

버전은 공식 npm 레지스트리의 안정 배포 메타데이터와 engines·peerDependencies를 확인했다. 정확한 설치 그래프는 후속 구현에서 lockfile로 확정한다.

| 패키지 | 정확 버전 | 역할 |
| --- | --- | --- |
| next | 16.4.3 | App Router·빌드·서버 실행 |
| react / react-dom | 각각 19.3.1 | 컴포넌트·렌더링, 동일 버전 유지 |
| @tanstack/react-query | 5.104.5 | 서버 데이터 상태 |
| axios | 1.20.0 | 브라우저 공통 요청 전송 |
| zustand | 5.0.16 | UI 공유 상태 |
| react-hook-form | 7.91.0 | 폼 상태·제출 |
| zod | 4.6.7 | 런타임 검증 |
| @hookform/resolvers | 6.0.0 | RHF·Zod 연결 |
| radix-ui | 1.8.1 | Primitives 통합 패키지. Themes가 아님 |
| ag-grid-community / ag-grid-react | 각각 36.2.0 | Community 본체와 React 연결, 동일 버전 유지 |
| echarts | 6.1.0 | 차트. 별도 React wrapper 기본 미추가 |

Radix는 공식 권장 통합 패키지에서 사용하는 Primitives만 가져온다. AG Grid·ECharts는 브라우저가 필요한 공통 컴포넌트 경계에서 연결한다. [HDR07 통합 조건](security/headers-csp.md#hdr07--확정-ui-도구의-csp-통합-조건)의 nonce·그리드 이미지 출처·스타일 적용 경로를 지킨다. 스타일·nonce CSP·hydration의 실제 검증은 공통 UI 상세 설계·구현에서 수행한다. AG Grid 36의 TypeScript 최소 5.8.3 조건도 선택한 6.0.3이 충족한다. 현재 버전 선택이 엄격한 CSP의 실행 통과를 뜻하지 않는다. [AG Grid 호환표](https://www.ag-grid.com/react-data-grid/compatibility/)

근거: [Next 메타데이터](https://registry.npmjs.org/next/16.4.3), [React](https://registry.npmjs.org/react/19.3.1), [React DOM](https://registry.npmjs.org/react-dom/19.3.1), [TanStack Query](https://registry.npmjs.org/@tanstack/react-query/5.104.5), [Axios](https://registry.npmjs.org/axios/1.20.0), [Zustand](https://registry.npmjs.org/zustand/5.0.16), [React Hook Form](https://registry.npmjs.org/react-hook-form/7.91.0), [Zod](https://registry.npmjs.org/zod/4.6.7), [resolvers](https://registry.npmjs.org/@hookform/resolvers/6.0.0), [Radix](https://registry.npmjs.org/radix-ui/1.8.1), [AG Grid](https://registry.npmjs.org/ag-grid-react/36.2.0), [ECharts](https://registry.npmjs.org/echarts/6.1.0), [Radix 패키지 권장](https://www.radix-ui.com/primitives/docs/overview/introduction).

## 4. 프론트 개발 도구

| 패키지 | 정확 버전 | 판단 |
| --- | --- | --- |
| typescript | 6.0.3 | 최신 7.0.2 대신 lint 생태계의 지원 범위 선택 |
| orval | 8.45.0 | 생성 시에만 사용, Node >=22.18 충족 |
| eslint | 9.39.5 | 최신 10.13.0 대신 Next 플러그인들과 공통 지원되는 9 사용 |
| eslint-config-next | 16.4.3 | Next와 동일 버전 |
| prettier | 3.9.10 | 코드 형식 검사 |
| @types/react / @types/react-dom | 각각 19.3.0 | React 19 타입 |
| @types/node | 24.19.2 | 실행 Node 24 계열에 맞춤. 최신 26 타입 미사용 |

Next의 typescript-eslint 의존 범위에서 최신 안정 8.72.0은 TypeScript >=4.8.4 <6.1.0을 지원한다. eslint-plugin-react 7.37.5, eslint-plugin-import 2.32.0, eslint-plugin-jsx-a11y 6.10.2는 ESLint 10을 peer 범위에 포함하지 않는다. 따라서 TypeScript 7·ESLint 10을 강제 설치하거나 peer 경고를 무시하지 않는다. 간접 lint 도구는 직접 중복 설치하지 않고 이 지원 범위를 만족하는 정확 해석 결과를 lockfile에 남긴다.

Orval은 React Query v5·Axios 출력을 명시하고 공통 mutator를 연결한다. 출력 기본값이나 예제의 mock 옵션에 의존하지 않는다. React Query 생성 지원은 [Orval 가이드](https://orval.dev/docs/guides/react-query/), 전송 연결은 [custom Axios](https://orval.dev/docs/guides/custom-axios/)를 따른다. 생성기 자체에 여러 출력 어댑터가 포함되어도 SWR·Angular·mock·MCP 기능을 프로젝트에 활성화하지 않는다.

근거: [TypeScript 6.0.3](https://registry.npmjs.org/typescript/6.0.3), [typescript-eslint 8.72.0](https://registry.npmjs.org/typescript-eslint/8.72.0), [Next lint 의존성](https://registry.npmjs.org/eslint-config-next/16.4.3), [React lint 플러그인](https://registry.npmjs.org/eslint-plugin-react/7.37.5), [import 플러그인](https://registry.npmjs.org/eslint-plugin-import/2.32.0), [a11y 플러그인](https://registry.npmjs.org/eslint-plugin-jsx-a11y/6.10.2), [ESLint](https://registry.npmjs.org/eslint/9.39.5), [Orval](https://registry.npmjs.org/orval/8.45.0), [Prettier](https://registry.npmjs.org/prettier/3.9.10), [npm](https://docs.npmjs.com/), [React 타입](https://registry.npmjs.org/@types/react/19.3.0), [DOM 타입](https://registry.npmjs.org/@types/react-dom/19.3.0), [Node 타입](https://registry.npmjs.org/@types/node/24.19.2).

## 5. 백엔드 직접 선택과 BOM 관리

독립적으로 버전을 선택하는 것은 Boot 4.1.2, MyBatis starter 4.1.0, springdoc 3.2.0이다. 나머지는 Boot BOM과 해당 starter가 관리하는 검증된 계열을 우선 사용한다. 개별 구성품의 최신 번호를 맞추려고 Spring·Jackson·드라이버를 따로 올리지 않는다.

| 대상 | 채택 버전 | 소유·사용 범위 |
| --- | --- | --- |
| Spring Boot | 4.1.2 | webmvc·security·validation·JDBC·Redis·Flyway 관련 Boot 모듈 |
| MyBatis Spring Boot starter | 4.1.0 | Boot 4.1 지원 계열 |
| MyBatis-Spring / MyBatis | 4.1.0 / 3.5.19 | starter 4.1.0 관리 값. core 별도 최신 강제 없음 |
| springdoc-openapi-starter-webmvc-ui | 3.2.0 | Boot 4.1 호환표의 3.1~3.2 중 최신 안정판 |
| Spring Framework | 7.0.10 | Boot BOM |
| Spring Security | 7.1.2 | Boot BOM |
| Spring Session Data Redis | 4.1.1 | Boot BOM, 공유 HttpSession |
| Spring Data Redis | 4.1.2 | Boot의 Spring Data BOM 2026.0.2 |
| Lettuce | 7.5.2.RELEASE | Boot BOM, Redis 연결 |
| HikariCP | 7.0.2 | Boot BOM, JDBC pool |
| PostgreSQL JDBC driver | 42.7.14 | Boot BOM, 런타임 의존성 |
| Flyway core + flyway-database-postgresql | 각각 12.4.0 | Boot BOM. PostgreSQL 지원 모듈 포함, 유료 기능 기본 미사용 |
| Hibernate Validator | 9.1.5.Final | Boot BOM, Bean Validation 구현체. ORM/JPA 도입을 뜻하지 않음 |
| Lombok | 1.18.48 | Boot BOM. compileOnly + annotationProcessor, 런타임 제외 |
| Jackson | 3.1.7 | Boot 기본 JSON 계열. Jackson 2 강제 교체 금지 |
| SLF4J / Logback | 2.0.20 / 1.5.38 | Boot BOM, 기존 기본 로깅 |
| 내장 Tomcat | 11.0.27 | Boot BOM, Servlet 6.1 |
| JUnit Jupiter | 6.0.3 | Boot BOM. JUnit 4나 Vintage 기본 미추가 |
| spring-boot-test / spring-boot-test-autoconfigure | 각각 4.1.2 | test 범위만. Spring 설정·컨텍스트 테스트 |

Boot 4의 모듈 분리에 맞춰 실제 starter 좌표와 자동 설정을 적용한다. PostgreSQL은 Flyway 지원 DB이며 실제 migration은 승인된 격리 DB에서 확인한다. Redis는 기본 세션 명령을 사용하며 최신 Redis 전용 기능을 추가로 활성화하지 않는다. Lettuce 연결·공유 세션·직렬화·만료·다중 인스턴스 동작은 후속 실행 검증 대상이다.

Spring Boot Test는 선택 도구를 몰래 기본 설치하지 않도록 최소 모듈과 JUnit으로 구성한다. spring-boot-starter-test 4.1.2에는 AssertJ·Mockito·Awaitility 등이 포함되므로 그대로 일괄 추가하지 않는다. 테스트 slice가 필요하면 Boot 4의 해당 전용 모듈을 검토하고 기본 JUnit/Spring 테스트 범위를 넘어가는 도구는 도입 조건을 기록한다. JUnit 검증은 org.junit.jupiter.api.Assertions를 기본으로 한다.

근거: [Boot 4.1.2 BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.2/spring-boot-dependencies-4.1.2.pom), [Spring Data BOM](https://repo.maven.apache.org/maven2/org/springframework/data/spring-data-bom/2026.0.2/spring-data-bom-2026.0.2.pom), [MyBatis starter 릴리스](https://github.com/mybatis/spring-boot-starter/releases/tag/mybatis-spring-boot-4.1.0), [MyBatis BOM](https://repo.maven.apache.org/maven2/org/mybatis/spring/boot/mybatis-spring-boot/4.1.0/mybatis-spring-boot-4.1.0.pom), [springdoc 호환표](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot), [Lombok JDK 지원](https://projectlombok.org/changelog), [Flyway PostgreSQL](https://documentation.red-gate.com/flyway/reference/database-driver-reference/postgresql-database), [Boot test starter 의존성](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-test/4.1.2/spring-boot-starter-test-4.1.2.pom).

## 6. 고정·업데이트·공개 저장소 원칙

- 위 버전은 기준일의 채택값이다. RC·beta·SNAPSHOT·canary 및 자동 latest 추적은 기본 금지한다.
- 프론트 직접 의존성은 정확 버전, npm package-lock.json 단일 잠금 파일로 관리한다. Node·npm 버전도 명시한다.
- 백엔드는 Boot 플러그인/BOM과 독립 starter 버전을 고정한다. Gradle Wrapper·Wrapper 배포 checksum·의존성 locking으로 재현성을 기록한다. 의존성별 verification-metadata.xml 체크섬 등록은 기본 적용하지 않으며 문서 전용 선택사항이다.
- 컨테이너는 정확 버전 tag를 사용하고 실제 구현 시 플랫폼에 맞는 digest를 잠근다. 이번 조사에서 이미지 게시와 Linux amd64/arm64 제공을 확인했으며 pull·실행은 하지 않았다.
- 초기 구성·릴리스·보안 권고 발생 시 지원 종료, 취약점, 라이선스와 잠금 그래프를 다시 확인한다. 안전한 패치는 묶음 호환성 검사 후 갱신한다.
- AG Grid는 Community만 포함한다. ECharts와 모든 의존성의 LICENSE·NOTICE를 보존한다.
- Redis 8은 AGPLv3·RSALv2·SSPLv1 선택 라이선스다. 공개 예제는 Redis 소스/바이너리를 저장소에 복제하지 않고 외부 공식 이미지를 사용한다. 실제 고객 납품·수정·배포 방식에 맞는 라이선스 검토가 필요하면 [대안](stack-alternatives.md)의 Valkey도 비교한다. 앱 전체 라이선스와 외부 서비스 라이선스는 구분한다. [Redis 공식 라이선스](https://redis.io/legal/licenses/)
- 실제 비밀값·운영 주소·개인정보·로컬 절대 경로를 예제·생성 spec·로그·lock 설정에 넣지 않는다. 설정 예제는 안전한 placeholder만 사용한다.

## 7. 확인 범위와 후속 작업

완료: 공식 릴리스·npm engines/peer 범위·Maven BOM·제품 호환표를 통한 선정, DB·Redis 이미지 태그 게시 확인, 기존 결정과 문서의 정합성 대조.

실행 범위: 의존성 설치·잠금 그래프·Java/Next 빌드와 local 기초 DB/Redis 연결을 확인했다. 상세는 [1차 변경 이력](change-history.md)과 각 모듈 README를 따른다. Orval 실제 생성 결과·업무 migration·실제 로그인/멀티서버·공통 UI/CSP 및 운영 배포 검증은 남아 있다. 이 버전표 자체는 실행 인증서나 취약점 없음의 증거가 아니다.

후속 구현 시에는 잠금 그래프에 peer 충돌·불필요한 선택 도구가 없는지 확인하고, 기본 빌드·타입·lint·생성 코드 검사와 PostgreSQL/Redis 통합 동작을 검증한다. 예제 기능 목록·공통 컨트롤 명세·인증 provider·운영 배포는 그 작업 시 설계한다. 이런 후속 사항은 이번 기본 구성 확정의 미결정 항목으로 취급하지 않는다.
