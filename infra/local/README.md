# 로컬 PostgreSQL·Redis

개인 PC에서 간편하게 접속하는 예제다. 공개된 가짜 테스트 자격 **testuser / 1234**를 사용한다.
공유 dev/stg/prod 또는 실제 서버에 사용하지 않는다. PostgreSQL testuser는 로컬 관리자이며
Redis testuser도 로컬 전체 권한이다. 프론트·백엔드는 IDE에서 별도로 실행한다.

## 실행

Docker Desktop의 Linux 엔진을 실행한 뒤 저장소의 `infra/local`에서 실행한다.
별도 자격 생성이나 .env 파일은 필요 없다.

```powershell
docker compose config --quiet
docker compose up -d --wait
docker compose ps
```

Docker Desktop에서는 `starter-project` 그룹 아래 `postgres`, `redis`로 표시된다.
`-p` 또는 COMPOSE_PROJECT_NAME을 지정하면 다른 프로젝트가 되므로 기본 실행에서는 지정하지 않는다.
공식 PostgreSQL 18.6·Redis 8.10.2 이미지를 digest로 고정한다. 없으면 최초 기동 시 다운로드한다.
최초 빈 PostgreSQL volume에는 starter_local DB와 testuser가 생성된다. 업무 테이블·migration·seed는 없다.

| 접속 | 호스트 | 포트 | DB/사용자 | 비밀번호 |
| --- | --- | --- | --- | --- |
| PostgreSQL | 127.0.0.1 | 25432 | starter_local / testuser | 1234 |
| Redis | 127.0.0.1 | 26379 | DB 0 / testuser | 1234 |

JDBC URL은 `jdbc:postgresql://127.0.0.1:25432/starter_local`이다.
백엔드 local 프로파일은 위 접속값을 기본 사용한다. IDE·Gradle 실행과 override는
[백엔드 실행 안내](../../backend/README.md#로컬-실행)를 따른다.
Redis는 인증·AOF everysec·noeviction·256 MiB 상한과 indexed Session용 `Egx` event를 사용한다.
로컬 Redis 사용자는 모든 key/channel을 허용하므로 `starter:local:session:v1` namespace의
created 패턴 구독과 DB 0의 del/expired 이벤트 구독이 가능하다.
이 구성은 운영의 최소권한·역할 분리·비밀 관리 계약을 대체하지 않는다.

## 중지와 데이터 보존

```powershell
docker compose stop
docker compose start --wait
```

컨테이너 이름은 `starter-project-postgres-1`, `starter-project-redis-1`이며,
데이터는 `starter-project_postgres-data`, `starter-project_redis-data` named volume에 보존한다.
PostgreSQL 18 volume 경로는 `/var/lib/postgresql`이다. Redis AOF는 최근 쓰기의 무손실을 보장하지 않는다.
기존 volume에서는 초기 자격을 자동 갱신하지 않는다.

이전 starter-local/nextjs-springboot-starter 프로젝트의 자원·volume·.private는 자동 이전하거나
삭제하지 않는다. .private가 남아 있어도 이 구성은 소비하지 않는다. 포트 충돌 시 다른 서비스를
임의 종료하지 않는다. 실패하면 이 프로젝트만 stop하고 volume을 보존한 채 원인을 확인한다.
최초 초기화의 부분 성공은 자동 rollback되지 않는다. 삭제·초기화는 별도 명시 승인 대상이며
`down -v`나 Docker prune을 일반 복구 절차로 사용하지 않는다.

## 검증 범위

Compose 정적 검사, 서비스 health, 프로젝트 label·포트 매핑, PostgreSQL TCP 인증 SELECT 1,
Redis 인증 PING·event 설정 조회·정확한 namespace 구독과 잘못된 비밀번호 거부를 확인한다.
앱 실행·Spring Session round-trip·Flyway·업무 스키마는 이 Docker 작업의 범위 밖이다.

2026-10-26 실제 검사: Docker Desktop 4.90.0 / Linux Engine 29.7.2 / Compose 5.5.1.
두 서비스 healthy, starter-project label·루프백 포트 매핑 및 Windows 호스트 TCP 연결을 확인했다.
컨테이너 TCP와 Docker Desktop 호스트 공개 포트를 경유한 PostgreSQL SELECT 1·Redis PING,
잘못된 비밀번호 거부, AOF everysec·event gxE(Egx의 정규화 표현), 정확한 created 패턴·
del/expired 채널 구독 ACK를 확인했다. 구독은 3초 timeout으로 종료했고 key 쓰기는 하지 않았다.
재시작·데이터 복원·앱 Session 통합은 검사하지 않았다.

백엔드의 local 기본값 연계와 실제 DB·Redis probe 및 Flyway 기동 검증은
[백엔드 검증 기록](../../backend/README.md#검증-상태와-인계)에서 관리한다.
