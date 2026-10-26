# Docker 기반 로컬 DB·Redis·테스트 환경 설계

작성일: 2026-10-22 / 갱신: 2026-10-26 / 상태: 사용자 결정에 따라 로컬 예제 구성을 단순화한다. 실제 실행·검증 결과는 infra/local/README.md가 기준이다.

## 방향

DB는 PostgreSQL 18.6, 공유 세션 저장소는 Redis 8.10.2로 확정했다. [확정 버전표](stack-versions.md)를 적용한다. Docker Compose는 이 두 서비스를 로컬에서 간편하게 띄우는 도구다. 프론트/백엔드는 IDE 또는 표준 런타임으로 실행한다. JUnit과 Spring Boot Test만 기본 포함하고 추가 자동화 도구는 기본 설치하지 않는다. 실제 DB 동작 확인에는 개발 데이터와 분리된 격리 DB를 사용한다. Testcontainers는 초기 기본 의존성에 포함하지 않는다.

Docker는 DB 제품이 아니라 실행 환경이다. DB 제품은 PostgreSQL로 확정했으며 MySQL 등으로 바꾸려면 migration·driver·테스트를 함께 변경한다. 로컬 구성은 운영 배포 설정으로 복사하지 않는다.

## 로컬 예제 범위와 사용자 흐름

| 산출물 | 책임 |
| --- | --- |
| infra/local/compose.yaml | starter-project 그룹의 postgres·redis. 앱은 IDE에서 실행 |
| infra/local/.env.example | 필요한 포트 변경 안내. 로컬 공개 테스트 계정 기본값 사용 |
| 로컬 테스트 자격 | testuser / 1234처럼 공개된 가짜 값. 비밀 생성·secret staging을 기본 요구하지 않음 |
| backend의 migration 원본 | DDL 이력과 앱 schema. 기본 Redis 세션은 JDBC table 미생성. local 합성 seed와 운영 DDL 분리 |
| 최초 시작 | Compose가 DB·테스트 계정과 Redis를 준비. backend local은 기본값으로 연결하고 Flyway를 적용. 실제 업무 테이블은 후속 설계 |
| README 실행 안내 | Docker 전제조건·환경 준비·시작·검사·중지·명시적 초기화·문제 해결 |

현재 목표는 docker compose up -d --wait로 로컬 DB·Redis가 준비되고 이후 시작·중지에 데이터가 보존되는 것이다. 운영 서버를 설치하는 단계가 아니다. 기본 접속값과 코드 변경 필요 여부를 구분하고 실제 검증한 명령만 infra/local/README.md에 기록한다.

## DB·네트워크 계약

| 항목 | 기본안 |
| --- | --- |
| 이미지 | 공식 postgres:18.6. 구현 시 플랫폼별 digest 잠금. latest/RC 금지 |
| 호스트 포트 | 127.0.0.1:25432 → container 5432, 변경 가능 |
| DB명 | starter_local, 개발 전용 |
| 볼륨 | 명시한 named volume, 기본 중지/재시작에 데이터 유지 |
| readiness | pg_isready 등 서비스 health + migration 성공 + 실제 앱 계정 연결 확인 |
| 인증 | trust 모드 없이 공개 테스트 자격으로 인증. local 예제에 한해 단일 DB 계정 허용, 운영 최소권한 계정 설계와 구분 |
| 로컬 전송 | 한 PC의 루프백·개발 Docker 경계에 한정해 평문 예외 허용. 운영 TLS 검증 계약 유지 |
| 로그 | 접속 비밀·SQL binding 값·실데이터를 출력하지 않음 |

Compose의 단순 depends_on은 DB 준비 완료를 의미하지 않는다. 함께 실행할 job은 service_healthy·service_completed_successfully 등 실제 목적에 맞는 조건으로 순서를 정한다. IDE에서 실행하는 backend는 Compose가 기다려 주지 않으므로 별도 준비 절차와 시작 실패 진단을 제공한다. [Docker startup order](https://docs.docker.com/compose/how-tos/startup-order/)

이미지 major별 PGDATA·volume 경로를 공식 문서로 확인한다. PostgreSQL 18+는 /var/lib/postgresql 아래의 버전별 데이터 배치에 맞춰 volume을 설계하며 과거 /var/lib/postgresql/data 예제를 무검토 복사하지 않는다. major 변경은 단순 image 교체가 아닌 데이터 upgrade 절차다. [공식 PostgreSQL 이미지](https://github.com/docker-library/docs/blob/master/postgres/README.md)

## 역할·migration·seed

- local 예제는 고정 테스트 계정 하나로 DB를 준비한다. 자동 자격 생성·관리자/앱/migrator 역할 분리·비밀 파일 전달은 기본 로컬 절차에서 제외한다.
- 테이블·인덱스·필수 초기 데이터의 원본은 backend Flyway SQL이다. Docker에 업무 schema 원본을 중복 작성하지 않는다. 현재 Docker 작업에서는 임의 테이블·seed·migration을 만들거나 실행하지 않는다.
- local 백엔드는 접속 기본값과 bootRunLocal을 제공하고 시작 시 Flyway를 적용한다. DB 연결·Redis probe·이벤트 구독 및 migration 0개 상태의 시작을 확인했다. Flyway 이력 테이블만 생성했으며 업무 테이블·로그인·다중 서버 연결 성공을 의미하지 않는다. dev/stg/prod의 별도 자격·DDL 권한·migration 실행 기준은 그대로 유지한다.
- 공식 PostgreSQL 이미지 초기화는 빈 volume에서만 실행된다. 재시작으로 기존 데이터를 삭제하거나 비밀번호를 바꾸지 않는다. 초기화 실패나 기존 volume 자격 불일치를 삭제로 자동 복구하지 않는다.
- Compose project 이름은 starter-project이고 named volume도 해당 project에 속한다. Desktop의 한 그룹에 postgres·redis가 표시되도록 기본 Compose 이름을 사용한다.

## 공개 저장소 경계

공개할 것은 Compose·migration·예제·실행 문서와 명시된 로컬 전용 가짜 테스트 자격이다. 실제 서비스 자격이나 개인정보는 포함하지 않는다. 실제 .env·자격 파일·volume 데이터·DB dump·로그·IDE 개인 설정은 추적하지 않는다. 로컬 환경변수 파일은 비밀 vault가 아니므로 파일 권한과 저장 위치를 제한한다.

.env가 Compose interpolation에 쓰이는 것과 컨테이너·Spring 환경에 전달되는 것은 다른 단계다. 각각 어떤 변수를 소비하는지 명세한다. POSTGRES_PASSWORD_FILE 등 image가 지원하는 file injection도 선택할 수 있으나 파일 자체가 공개되지 않게 해야 한다.

local의 루프백·개발용 인증서/평문 예외가 staging/production으로 전파되지 않도록 파일·프로필을 분리한다. 운영 자격/주소가 들어오면 개발 bootstrap은 실행을 거부하도록 설계한다.

## DB 검증과 향후 자동화 참조

DB 검증은 로컬 Compose 또는 외부 서비스로 준비한 격리 DB/Redis를 대상으로 한다. 아래 연결·격리 기준은 자동 검증 도입 시에도 적용하며, 이 절 자체가 테스트 라이브러리·코드·CI의 기본 설치를 요구하지 않는다. 사람이 쓰는 개발 DB와 다른 DB·자격·Redis namespace를 사용한다. 같은 DB major·동일 migration을 적용하고 합성 fixture만 생성한다. 개인/운영 DB로 자동 fallback하지 않는다.

Testcontainers는 테스트 자원의 자동 생성·정리가 실제로 필요할 때 도입한다. 도입 시 Docker가 없는 해당 job은 명확히 실패·미실행으로 보고하고 외부 격리 서비스 경로를 유지한다. H2로 조용히 대체하지 않는다. Boot의 해당 버전 Testcontainers/ServiceConnection 지원 범위를 확인한다. [Boot Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)

컨테이너 종료·정리는 자신이 만든 임시 자원에 한정한다. Docker 전체 prune·기존 volume 삭제는 실행하지 않는다. AI의 앱 시작·migration·seed·테스트를 통한 DB 변경은 공통 [DB 실행 규칙](../common-rules/docs/conventions/common/database-access.md)의 승인 범위 안에서만 수행한다.

## 수명주기·완료 기준

- 시작/재시작: 기존 데이터를 유지, health와 schema·앱 계정 연결을 확인.
- 중지: 컨테이너 중지만 수행, volume 삭제 없음.
- 초기화: 명시한 프로젝트의 local volume만 삭제하는 별도 파괴 명령. 대상·데이터 손실·승인 안내 필수.
- 현재 Docker 검사: 프로젝트 그룹·포트·health·테스트 인증 접속·잘못된 비밀번호 거부·데이터 볼륨 확인. migration·업무 계정 격리·실제 세션은 해당 후속 구현 단계에서 검증.
- CI: 자동 검증을 도입할 때 동일 migration과 격리 DB를 사용하고 비밀·dump·volume가 artifact에 포함되지 않게 한다. DB 테스트 job은 기본 생성하지 않는다.
- 문서: 실제 지원 OS/Docker/Compose 버전·명령·대기 조건·예상 결과를 기록.

실제 실행 여부와 검사 결과는 infra/local/README.md 및 담당자의 완료 보고를 확인한다. 문서상의 요구와 실행 증거를 구분한다.

## Redis 로컬 서비스

Compose는 공식 redis:8.10.2를 함께 시작하고 127.0.0.1:26379를 기본 호스트 포트로 제안한다. local 예제용 고정 테스트 자격으로 인증하고 외부 인터페이스에 published port를 열지 않는다. 비밀 파일 준비 절차는 요구하지 않는다. 로컬 named volume·AOF everysec은 시험 편의 기본안이며 운영 HA/영속화 정책을 대신하지 않는다.

앱의 실제 세션 쓰기/읽기로 준비 상태를 확인한다. 정상 중지는 volume을 지우지 않는다. local 예제는 접속 편의를 위해 동일한 가짜 테스트 자격을 사용할 수 있다. 실제 환경에서는 서비스별 자격을 분리한다. 캐시·rate limiter 추가는 자동 활성화하지 않는다. 상세 계약은 [Redis 공유 세션 기반](authentication-foundation.md)을 따른다.

## 다른 실행 환경으로의 이식 계약

| 경계 | 필수 계약 |
| --- | --- |
| 제품 런타임 | Docker 설치·socket·Compose 파일·container inspect·고정 volume 경로에 의존하지 않음 |
| 접속 정보 | DB/Redis host·port·database·username·secret·TLS·CA·timeout을 외부 설정으로 주입 |
| 주소 | IDE에서는 loopback/호스트 port, 컨테이너 배포에서는 서비스 주소, 관리형 서비스에서는 해당 endpoint. 코드 변경 대신 설정 변경 |
| 초기화 | migration/준비 로직의 원본을 Docker init 디렉터리에만 두지 않음. Gradle/Java/CLI 등 배포 환경에 맞는 실행 경로로 재사용 |
| 운영 배포 | local Compose·평문·개발 자격·고정 로컬 port를 자동 사용하지 않음. 필수 운영값 누락 시 실패 |
| 테스트 | Compose는 기본 편의 경로이며 Testcontainers는 조건부 도구. 명시적 외부 격리 서비스 경로에서도 같은 계약 검증 가능 |

운영 Redis의 standalone/Sentinel/Cluster·관리형 기능은 접속 주소만 바꾸면 무조건 호환된다고 보장하지 않는다. topology·ACL·event·serializer·TLS를 명세하고 검사한다. Docker 독립성은 외부 서비스나 환경별 보안 설정이 필요 없다는 뜻이 아니다.

필수 이식 시험: Docker 없는 호스트에서 외부 격리 DB/Redis를 주입해 앱 시작·세션 read/write·migration·대표 API 응답을 확인한다. 이 검사는 Actuator를 전제로 하지 않는다. local hostname이 fallback으로 사용되지 않는지와 JAR/Node 산출물의 Docker 도구 의존성 부재를 검사한다. 현재 이 시험은 미실행이다.
