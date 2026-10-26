# 변경 이력

## 2026-10-26

- Docker Compose의 `starter-project` 그룹에 PostgreSQL 18.6과 Redis 8.10.2를 구성하고 공식 이미지 digest를 고정했다.
- PostgreSQL `127.0.0.1:25432`, Redis `127.0.0.1:26379`로 노출하고 자원 제한·healthcheck를 적용했다.
- DB `starter_local`과 공개된 가짜 자격 `testuser / 1234`로 별도 비밀 준비 없이 개인 PC에서 접속하도록 단순화했다. 공유 dev/stg/prod·운영에는 사용하지 않는다.
- `starter-project_postgres-data`, `starter-project_redis-data` named volume과 Redis AOF everysec으로 정상 중지 후 데이터를 유지한다. 기존 자원·데이터·비밀을 삭제하거나 초기화하지 않았다.
- Redis 인증과 indexed Session용 `Egx` 이벤트를 구성했다. Compose 정적 검사, 두 서비스 health·project label·루프백 포트, PostgreSQL 인증 `SELECT 1`, Redis 인증 `PING`·event 조회·정확한 채널 구독 및 잘못된 비밀번호 거부를 실제 확인했다.
- 비밀 생성·staging·역할 분리 스크립트를 제거하고 준비·접속·중지·복구 안내를 최종 구성에 맞췄다. IDE 앱 실행과 local 기본값 연계는 백엔드 README로 연결한다.
- 업무 스키마·로그인·운영 배포는 범위 밖이다. 재시작 후 데이터 복원과 장애·운영 보안 검증은 완료로 표시하지 않는다.
