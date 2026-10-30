# 로그·요청 추적·접근 및 보안 이력 설계

기준일: 2026-10-30 / 상태: 로그·접근/보안 이력 기반과 local 콘솔·SQL 파라미터 가독성 개선 구현. [가독성 보완 기준](readability-and-local-logs.md), [검증표](../../backend/VERIFICATION.md)의 실제 확인·한계를 함께 읽는다. [전체 정책](backend-foundation.md), [이력 모델](backend-data-model.md) 적용.

> 로컬 배너·기동 완료 표시·색상·메시지 가공은 [추가 개선 기준](readability-and-local-logs.md)을 우선 적용한다. 공유 환경의 JSON 파일·회전·보관과 감사 이력은 기존 기준을 유지한다.

## LOG01 — 출력·레벨 (2-1~2-3)

| 환경 | 출력 | 형식 |
| --- | --- | --- |
| local | 콘솔만 | UTC yyyy-MM-dd HH:mm:ss.SSS·레벨·traceId·내부 사용자ID·logger·안전한 내용 |
| dev/stg/prod | 파일 기본, 콘솔 기본off | JSON, timestamp는 같은 밀리초 UTC 문자열 |

SLF4J/Logback·Boot 기본 로깅 재사용. root INFO, application INFO, framework WARN(기존 필요한 기동 INFO 허용), local SQL 전용 logger DEBUG. local에서만 우리 패키지 상세 수준 선택 허용. 모든 logger DEBUG로 SQL을 여는 방식 금지. 접근이력과 실행 로그는 서로 대체하지 않는다.

파일: ./logs/application.log는 INFO 이상, ./logs/error.log는 ERROR만. ERROR는 두 파일에 중복 저장하는 의도된 계약, WARN은 application만. 각 SizeAndTimeBasedRollingPolicy: 날짜별 또는100MB, archive/application/yyyy-MM-dd와 archive/error/yyyy-MM-dd 아래 순번·.gz, maxHistory30, totalSizeCap 각각3GB/1GB. 상한은 각 압축 archive 기준으로 현재 활성 파일까지 합한 디스크의 엄격 상한 보장은 아님. startup 보관 정리, 로그 경로 쓰기권한/rollover 오류 확인.

여러 프로세스가 같은 파일에 쓰지 않도록 logs/{환경}/{serverId}/를 기본 실제 경로로 사용, serverId는 외부 값 또는 host+port+시작UUID(100자 이하). 노드 구분은 로그 경로용이며 auth namespace에 넣지 않는다. local은 파일 appender가 없어야 함. 공유 환경 출력 경로 불가 시 안전한 콘솔 ERROR로 원인만 남기고 ready 금지, 파일 오류를 무시한 정상 기동 금지. 중앙 수집기·APM·Actuator 기본 미추가. 컨테이너 콘솔 수집 환경은 추후 명시 출력 설정으로 교체 가능.

## LOG02 — 요청 추적·문맥 (2-4~2-5)

RequestContext는 traceId(UUIDv4 서버생성), actorId?, clientIp, serverId. MDC는 필터 finally clear, 스레드풀 잔여 금지. 임의 X-Request-ID/X-User/Forwarded 헤더를 인증/신뢰 근거로 삼지 않는다. 프록시 CIDR과 직접 peer 확인 후에만 전달IP 사용, 기본 직접 연결주소. IPv4/IPv6 정규화.

요청 종료 한 줄: method, routeTemplate, status, elapsedMs, traceId, actorId?. 실제 URI 대신 가능한 MVC 템플릿(/users/{id}) 사용, 매칭 실패는 UNMATCHED 고정. 쿼리스트링·헤더·본문·응답 body·인증 쿠키·원문ID경로는 기록하지 않음. 정적 자원 요청은 제외, API와 인증 흐름은 포함. 시간은 monotonic timer로 계산, 시각은 UTC Clock. async/error dispatch 이중 기록 금지.

CurrentActor/RequestContext 접근 도구를 제공하되 HTTP 요청 없는 migration/job 문맥은 SYSTEM/null, ThreadLocal에서 임의 가짜 계정 반환 금지. 미래 async는 명시 전달/정리 계약 없이 자동 전파하지 않는다.

## LOG03 — 예외·민감정보 (2-6)

예상 업무 오류는 code와 안전한 위치를 INFO/WARN, 예상하지 못한 오류는 ERROR에 정제 원인·스택 위치 기록. 기존 진단 writer를 확장하며 getMessage·중첩cause·SQLState 설명·파일경로를 무검토 출력하지 않음. 예외는 최종 책임 경계에서 한 번 기록, 공통 API는 api-contract의 고정 필드만.

필드 allowlist가 기본이며 정규식 마스킹만으로 전체 raw 본문 로그를 허용하지 않는다. CR/LF와 제어문자로 로그 위조 불가, 자유 문자열은 길이 제한·이스케이프. 타 라이브러리의 JDBC bind/HTTP wire/Redis command logging도 켜지지 않는지 검사. logger DEBUG 설정은 필드 정제 우회 권한이 아니다. 로그에 사용자ID/IP가 필요하면 접근·보관 대상 개인정보로 취급.

## SQL01 — local 쿼리와 파라미터 보기

2026-10-29 사용자가 로컬 로그의 가독성과 파라미터 확인을 요청했다. 기존의 “파라미터를 전부 숨김” 규칙을 [설명 문구와 로컬 로그 개선](readability-and-local-logs.md)의 2~4절로 대체한다. 기존 MyBatis 진단 기능을 사용해 SQL·실제 바인딩 순서·안전하게 가공한 파라미터를 구분해서 보여준다. 인증 SQL과 민감값은 숨기고, 공유 환경에서는 상세 SQL/파라미터를 출력하지 않는다.

측정값 mapperElapsedMs는 MyBatis 매핑을 포함한 Mapper 호출 시간이다. 순수 DB 실행시간으로 표시하지 않는다. 동적 SQL과 줄바꿈을 유지하고, 실제 SQL·값·연결·트랜잭션을 바꾸지 않는다. slow 요약은 기존처럼 statementId·시간·결과만 사용한다. 상세 표시·허용 값·길이 제한·민감정보 검사는 연결 문서에서 관리한다.

## AUD01 — 대상 지정·수집 (2-8~2-9)

서비스 업무 경계의 @AccessAudit(resource=고정코드, action=READ/SEARCH/CREATE/UPDATE/DELETE/EXPORT, policy=REQUIRED/BEST_EFFORT)를 제공한다. 구현은 Spring AOP 지원만 사용, 필수 범위에서 자기호출로 advice 우회 금지. RESOURCE는 서버 registry에서 API와 권한/민감등급/허용target 추출기를 연결, 요청 문자열·임의SpEL 실행으로 정책 선택 금지.

민감정보 조회/다운로드 REQUIRED, 일반 기능 BEST_EFFORT. 대상은 공개DTO의 내부ID 등 명시 추출기만 사용하고 arguments/toString/response 전체 직렬화 금지. 목록은 건별 이력 폭증 대신 검색 행위 하나, 검색어·조회내용 미저장. 승인된 정적 resource·action·대상ID·result만. 화면 진입 이벤트는 후속 프론트 기능이며 서버 데이터 접근 이력 대체 불가.

ACCESS_AUDIT는 발생시각·내부주체·resource/action·대상·outcome·traceId·serverId·신뢰IP와 선택 제한UA를 저장. UA는 기본 미수집, 켤 때256자·제어문자 제거. 임의 사유 문구 대신 REASON_CODE, 실제 접근 사유 입력 기능은 도입 시 별도 제한/PII 검토.

## AUD02 — 저장과 실패 의미 (2-10)

- 감사 writer는 별도 bean/REQUIRES_NEW 짧은 DB 트랜잭션으로 일반 업무 rollback과 분리. 임시 테이블 업무 연결을 재사용하지 않고 이력 writer에 임시 데이터 조회를 위임하지 않음.
- REQUIRED 민감 READ/SEARCH는 STARTED를 저장 확인한 뒤 업무 조회, 결과 반환 전 SUCCESS/FAILURE 이벤트를 저장 확인. STARTED와 종료는 OPERATION_ID로 연결한 별도 append 행. 실패하면503 AUDIT_UNAVAILABLE, 민감 결과를 반환하지 않음. 메서드 반환은 네트워크 전달 완료가 아니므로 SUCCESS는 서버 처리 완료 의미다.
- BEST_EFFORT는 결과 확정 후 이력 INSERT, 저장 실패는 결과 유지+ERROR·정제 trace/이벤트ID. 업무 실패는 원래 오류 유지하고 이력 실패로 덮지 않음.
- 변경의 성공/실패는 트랜잭션 afterCommit/afterCompletion에 연결, 메서드 return만으로 성공 처리 금지. 커밋결과 불명확하면 UNKNOWN, 성공으로 추측 금지.
- 반드시 남아야 하는 민감 변경/권한 변경의 SUCCESS 감사는 업무와 같은 DB 트랜잭션으로 INSERT해 실패 시 업무 rollback, STARTED/FAILURE는 독립 writer로 보존. afterCommit 이력 실패 때문에 이미 커밋된 변경을 취소했다고 응답하지 않음. REQUIRED 변경을 범용 어노테이션 하나로 자동 보장한다고 주장하지 않는다.
- 다운로드/stream은 아직 미구현. 도입 시 송신 전 이력 저장·중간 실패 의미를 별도 계약, 지금 완전 송신 성공 이력을 구현한 것으로 표시하지 않음.
- 권한/인증 거부는 서비스 advice 이전에 발생하므로 공통 Security handler가 registry로 대상 판별·DENIED 기록. 익명 시도는 actor=null. resource를 판별 못한 요청은 보안이력만, 사용자 입력으로 대상 기능 조작 금지.
- pool 대기/교착: 이력 별도 연결이 필요하므로 outer transaction 동시성 대비 연결 여유·짧은 타임아웃 검증. 무제한 대기·메모리 큐에 넣고 영속 성공 주장 금지.

## AUD03 — 보안 사건·조회·보관 (2-11~2-12)

SECURITY_AUDIT 사건: LOGIN_SUCCESS/FAILED/LIMITED, LOGOUT/LOGOUT_ALL, TOKEN_REUSED/REVOKED/EXPIRED, CONCURRENT_LOGIN_DENIED/REPLACED, ACCOUNT_DISABLED/PASSWORD_CHANGED/ROLE_CHANGED, AUTH_STORE_FAILURE. TTL cleanup이 개별 만료 로그를 항상 발행한다고 가정하지 않음; 토큰 만료 접근과 cleanup 확정 이벤트의 구분을 문서화한다.

발급/갱신 이력 실패는 새 자격을 남기지 않고503. 로그아웃/차단은 이력 실패해도 폐기를 취소하지 않음. 로그인 실패 계정 raw ID를 이력에 남기지 않고 확인된 내부ID 또는 익명. 보안 store 장애로 DB 이력까지 실패하면 정제 ERROR+trace, 이력 누락 사실을 검증 기록에 남긴다.

조회 API는 AUDIT_READ/SECURITY_AUDIT_READ 각각 필요, 날짜범위 필수 최대31일·page최대100·기본시각DESC+PK. 조회 자체도 ACCESS_AUDIT REQUIRED READ로 기록, 감사 writer는 다시 advice에 들어가지 않음. 조회 응답에는 내부필요 필드만, 기본 UI에는 IP 축약. 전체 IP가 필요한 공개 계약은 별도 권한/사유 결정 후 제공.

이력 runtime UPDATE/DELETE 금지. local retention30일, shared는 환경별 필수 설정이며 개인정보/법적 정책 확인 전 운영 배포 완료로 표시하지 않음. 자동 대량 삭제 배치 미제공, 담당자 runbook에 만료범위 조회→백업/대상확인→별도 승인 삭제→기록 절차. 변경 불가 DB 설계는 DBA 변조 방지·WORM 보장을 뜻하지 않으며 외부 불변 저장 연계는 운영 결정 사항.

## 완료 검증

local 콘솔만·shared 두 파일·ERROR 중복·rollover/압축/용량·동일 날짜형식, trace/MDC 동시 요청 분리, SQL 줄바꿈·시간·안전한 파라미터 표시·민감값 비노출, 이력 STARTED/종료/거부/rollback/failure·권한·재귀 방지를 검증한다. 합성 민감 표식을 로그·API·DB 감사에 넣어 금지 경로에서 발견되지 않는지 확인한다. 보안 상세 시험은 [검증표](backend-handoff.md).
