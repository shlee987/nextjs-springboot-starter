# 백엔드 공통 기반 API·인계·검증 계약

기준일: 2026-10-30 / 상태: 공통 기반 구현·실연동·독립 리뷰 완료. 최신 API와 추가 파일 기능의 실제 확인 범위는 [현재 현황](foundation-status.md) 및 [백엔드 검증표](../../backend/VERIFICATION.md)를 따른다. 아래 내용은 구현 시 유지할 API·검증 기준이다.

## 1. 공통 API와 타입 (4-1~4-2)

[api-contract.md](api-contract.md)의 성공/오류 형식 유지. 아래 응답 필드는 envelope의 data 안에 들어간다. ID string, 날짜 string, page/size/건수는 안전 정수 number. 메서드·경로는 정확히 등록하며 임의 /api/** permitAll 금지. 모든 인증/개인 응답 Cache-Control:no-store, Vary/쿠키가 공통 handler에서 소실되지 않음. 204/HEAD 본문 없음.

| 메서드·경로 | 인증/CSRF·권한 | 요청 | 성공 data/상태 |
| --- | --- | --- | --- |
| GET /api/auth/csrf | 미인증 허용, IP한도 | 없음 | {token:string,headerName:"X-CSRF-TOKEN"},200 |
| POST /api/auth/login | JWT불필요, Origin/CSRF필수 | {loginId,password} | CurrentUser + accessExpiresAt,200; 쿠키로만 token 발급 |
| POST /api/auth/refresh | refresh쿠키, Origin/CSRF필수 | 본문 없음 | {accessExpiresAt,absoluteExpiresAt},200 |
| POST /api/auth/logout | access 또는 refresh로 현재 sid 확인, Origin/CSRF필수 | 없음 | 204. 무효/이미폐기/없음도 동일 |
| POST /api/auth/logout-all | access, Origin/CSRF필수 | 없음 | 204 |
| GET /api/auth/me | access, 활동 연장 | 없음 | CurrentUser,200. 미인증401(anonymous200 금지) |
| HEAD /api/auth/me | access, 활동 연장 없음 | 없음 | 200, 본문 없음. 갱신 전 상태 확인용 |
| PUT /api/auth/password | access, Origin/CSRF필수 | {currentPassword,newPassword} | 204, 전체 sid폐기·쿠키삭제·재로그인 |
| GET /api/users/me | access+USER_READ_SELF | 없음 | 자기 기본 프로필,200 |
| GET /api/users | access+USER_READ, REQUIRED 접근이력 | q?,page=0,size=20,sort=idAsc/nameAsc/nameDesc | PageData<UserSummary>,200 |
| GET /api/users/{userId} | access+USER_READ+PERSONAL_INFO_READ, REQUIRED 접근이력 | userId string숫자 | 사용자 기본 프로필+선택 이메일,200 |
| GET /api/departments | access+DEPARTMENT_READ | page,size,q?,sort=idAsc/nameAsc/nameDesc | PageData<DepartmentSummary>,200 |
| GET /api/audits/access | access+AUDIT_READ, REQUIRED 조회이력 | from,to,page,size,actorId?,resourceCode? | PageData<AccessAuditSummary>,200 |
| GET /api/audits/security | access+SECURITY_AUDIT_READ, REQUIRED 조회이력 | from,to,page,size,eventCode?,targetUserId? | PageData<SecurityAuditSummary>,200 |

CurrentUser = {userId,loginId,displayName,departments:[{departmentId,departmentName,primary}],roles:string[],permissions:string[]}. 순서·중복 제거 고정, 개인정보 이메일/내부token 없음. login은 {user:CurrentUser,accessExpiresAt} 구조로 명확히 감싼다. UserSummary={userId,displayName,status,departments:[{departmentId,departmentName}]}, 프로필은 여기에 loginId,email?,createdAt,updatedAt 추가(이메일 없으면null). DepartmentSummary={departmentId,departmentCode,departmentName,parentId:null|string,sortOrder:number,active:boolean}. 내부 PASSWORD_HASH/키/상태store 객체는 어떠한 응답에도 없음.

q는0~100자·누락=전체 허용범위, LIKE 특수문자는 문자로 escape, 정렬은 enum→상수. from/to는 밀리초 UTC문자열, from<=시각<to, 최대31일. audit 기본 occurredAt DESC,ID DESC, 공개필드는 감사모델 중 내부필요정보로 allowlist; LOGIN_REFERENCE/USER_AGENT/원문IP는 제외, clientIpMasked만. 이력에는 auditId/occurredAt/actorUserId/resourceCode/actionCode/targetType/targetId/outcome/reasonCode/traceId. security에는 eventId/occurredAt/eventCode/actorUserId/targetUserId/outcome/reasonCode/traceId.

PUT password는 LOCAL_CREDENTIAL 보유자만, 미보유는409 PASSWORD_LOGIN_UNAVAILABLE. 현재 password 오류는400 CURRENT_PASSWORD_INVALID(로그인된 본인에게만), 새 password15~128자 및 알려진 취약 예제목록 검사, 기존값 재사용400 PASSWORD_UNCHANGED. 문서 전용 선택 도구 없이 해시 encoder 사용. loginId가 허용 패턴/길이를 벗어나면400 VALIDATION_FAILED(계정 존재와 무관), 비밀번호는 로그인 요청에서 빈 값/128 code point 초과만400이고 생성 정책의 최소15자는 로그인 단계에서 재강제하지 않는다. 신규/변경에는15~128자 정책을 적용한다. loginId/비밀번호 잘못된 JSON형식은400, 정상형식 자격 실패는401 LOGIN_FAILED. 행 없는 사용자/허용범위밖 사용자404 NOT_FOUND. 인증 전 권한·존재정보 공개 금지.

사용자 비활성화/역할 할당/역할 권한 변경/부서 이동은 이번에 서비스·Mapper·원자 폐기/검증 계약을 구현하지만 관리 CRUD API/화면은 후속. 검증은 승인된 테스트에서 해당 Service를 호출하며 운영 fault/test endpoint를 만들지 않는다. 기초 테스트 화면·공통 UI 전체는 자동 착수하지 않는다.

### 오류 코드 추가

| code | HTTP | 프론트 동작 |
| --- | --- | --- |
| LOGIN_FAILED | 401 | 통일된 로그인 실패, 계정 존재/상태 노출 없음 |
| TOKEN_EXPIRED | 401 | 일반 API에서만 제한된 갱신 고려 |
| TOKEN_REVOKED / TOKEN_REFRESH_REUSED | 401 | 갱신 반복 금지, 사용자 캐시 정리·재로그인 |
| CONCURRENT_LOGIN_DENIED | 409 | 기존 로그인 때문에 새 로그인 불가 안내 |
| ORIGIN_REJECTED | 403 | 요청 출처 오류, 자동재로그인 금지 |
| CURRENT_PASSWORD_INVALID / PASSWORD_UNCHANGED | 400 | 해당 폼의 고정 안내 |
| PASSWORD_LOGIN_UNAVAILABLE | 409 | 자체 비밀번호 변경 불가 |
| AUTH_STATE_UNAVAILABLE / AUDIT_UNAVAILABLE | 503 | 일시 장애 안내, 로그인 상태를 임의 만료로 취급하지 않음 |

기존 VALIDATION_FAILED 등 코드는 유지한다. 오류 title은 안전한 HTTP 제목, 표시 메시지는 프론트 코드표가 소유한다. 권한 부족403과 미인증401·외부 장애503을 혼합하지 않는다. login/refresh의401을 공통 인터셉터가 다시 갱신하지 않는다.

## 2. OpenAPI·Orval (4-1~4-3)

- springdoc: local 기본 enabled, dev/stg/prod 기본false. local 서버는 루프백, Swagger 본문에 실제개인정보/키/토큰 예제 금지.
- API 목적·제약·필요 권한·CSRF·audit policy·정상/오류 code·transaction·부수효과를 명세. operationId 고정, response generic 구체타입, 공통 어노테이션 재사용. 어노테이션 문서는 실행 권한을 대체하지 않음.
- cookieAuth(실제환경 access쿠키)·CSRF header 모델, login/refresh의쿠키전용발급 설명. Swagger authorize에 JWT를 붙여넣는 별도 Bearer 경로를 임의 추가하지 않음. Try it out도 실제 login/CSRF와 Origin을 사용, 인증 영속 저장off·외부 CDN/validator/분석 없음.
- schema의 날짜는 custom string pattern+UTC설명(표준RFC3339 date-time이라고 거짓 표기 금지), 큰ID는string/digitpattern, decimal은string. 날짜의 date형식은 full-date와 일치하므로 date 가능.
- 생성 spec은 docs/api/openapi.json(실제 구현 후 신규 생성 예정), 환경 URL/개인값 제거·servers는상대 /, 고정operationId. frontend Orval은이 파일입력·기존Axios mutator·React Queryv5 생성, generated/ 직접수정금지.
- API변경→승인된 앱실행에서spec export→Orval→타입/빌드확인. 일반 프론트build가 backend실행/DB에 의존하지 않음. 지금 빈spec/가짜생성결과로 완료표시하지 않음.

## 3. 프론트 인증 연결 (4-4~4-5)

브라우저는 같은 출처의 /api를 사용한다. local은 기존 Next rewrite, 공유 환경은 신뢰 프록시를 사용한다. local 브라우저 호스트는 localhost로 통일해 127.0.0.1과 쿠키가 분리되지 않게 한다. JWT/refresh는 HttpOnly 쿠키로만 전달하고 응답 본문에 노출하지 않는다. Zustand는 표시용 사용자 상태, TanStack Query는 서버 데이터를 관리하며 토큰·비밀번호를 영속화하지 않는다.

1. CSRF 조회·메모리 보관 → 로그인 → 교체된 CSRF 재조회 → me 조회. 모든 상태 변경 요청에 X-CSRF-TOKEN을 전송한다.
2. 일반 API의 TOKEN_EXPIRED에 single-flight 갱신을 적용한다. 원인 불명·폐기401은 자동 갱신을 반복하지 않는다. Access가 없는 UNAUTHENTICATED는 앱 초기 인증 복원 흐름에서만 갱신1회를 허용한다.
3. 탭 간 Web Locks의 동일 origin refresh-lock을 사용한다. 잠금 획득 후 HEAD /api/auth/me로 다른 탭이 갱신했는지 확인(200이면 재갱신 안 함, 401이면 갱신1회). 이 확인은 활동 시간을 연장하지 않는다. BroadcastChannel에는 상태 변경/완료 신호만 전달하고 토큰은 넣지 않는다. 잠금 미지원 브라우저는 자동 갱신을 비활성화하고 만료 시 재로그인한다. 비원자 localStorage 잠금으로 안전을 보장한다고 주장하지 않으며 지원 브라우저는 후속 프론트 설계에서 검증한다.
4. GET/HEAD 자동 재시도는1회다. 변경 요청은 JWT 검증 단계에서 업무 진입 전401임이 보장되고 해당 API가 허용한 경우만1회 가능하며, 기본 자동 재전송은 비활성이다. 네트워크 오류·timeout·응답 유실·503에서 변경 요청을 재시도하지 않는다.
5. 갱신 성공은 쿠키를 교체하며 프론트가 토큰 해독으로 권한을 판단하지 않는다. 응답 유실·재사용 탐지는 재로그인으로 처리한다. 로그아웃·사용자 전환은 진행 중 조회 취소 → Query 캐시 제거 → Zustand 사용자 상태 제거 → 다른 탭 통지 순서다. 이전 응답이 캐시를 다시 채우지 않도록 인증 세대를 확인한다.
6. 권한·CSRF의403은 별도 안내한다. CSRF 오류는 토큰 재발급만 하고 변경 요청은 자동 재전송하지 않는다. 429는 Retry-After를 존중하고, 503은 일시 장애로 표시해 무한 로그인 이동을 막는다.

UI 상세와 decimal 계산 예제는 후속 프론트 설계의 필수 항목이다. 프론트 작업을 시작할 때 backend-foundation의 숫자 정밀도·UTC 파싱 계약을 다시 안내한다. 이번 문서 작성은 프론트 UI 개발 지시가 아니다.

## 4. 실행·초기화 안내 (4-6~4-7)

기존 starter-project PostgreSQL·Redis, IDE 백엔드 bootRunLocal, 프론트 npm run local·환경별 build·공통 start를 유지한다. local DB 기본값은 testuser/1234, 합성 로그인 계정은 데이터 모델을 참조한다. 프론트의 dev는 공유 개발 환경만 의미하고 npm run dev를 로컬 명령으로 추가하지 않는다.

모듈 README는 JDK·Node 설정, 포트25432/26379/8080/3000, 키 초기화, Flyway 적용 위치, 쿠키·CSRF, 저장소 전환, 동시 로그인 정책, 로그 위치와 실패 진단을 포함한다. Docker는 서비스 준비 도구이며 런타임이 Compose·socket·volume을 호출하지 않는다.

DB를 사용하는 테스트·앱 시작은 기존 공통 DB 실행 승인 기준을 적용한다. 실제 DB·Redis namespace, 명령, DDL·seed·키 생성·쓰기 영향, 백업·복구·정리 범위를 먼저 명시한다. 개발자의 평소 IDE 실행과 AI 실행 승인은 구분한다. compose down -v·Flyway clean을 일반 절차로 넣지 않는다.

## 5. 검증표 (4-8~4-10)

모든 항목은 현재 미실행이다. 구현 파일·테스트명·명령·시각·대상·결과·누락을 ID에 연결한다. 기존1차 JUnit·빌드는 새 기능의 통과 증거가 아니다. JUnit·Spring Test를 사용하고 AssertJ·Mockito·Security Test·Testcontainers·Playwright·Actuator를 자동 추가하지 않는다. 실제 쿠키 HTTP 시험은 Java HttpClient 등 기존 도구로 수행할 수 있다.

| ID | 필수시나리오·통과기준 |
| --- | --- |
| BF01 | 구조/Controller→Service→Mapper/설정누락·틀린enum·4환경키·local예외격리 |
| BF02 | 날짜3형식·윤년/불가능날짜·UTC·밀리절삭·API/DB/log왕복,JWT NumericDate유지 |
| BF03 | 큰ID·BigDecimal문자열/안전건수·null/[]·검증오류·누락메시지fallback |
| BF04 | 따옴표대문자컬럼→camel자동매핑·조인별칭·정렬SQL주입/상한/동일count조건 |
| BF05 | DB01~DB07 실제PG연결·checked/runtime rollback·여러Mapper TEMP/cleanup·pool재사용 |
| LG01 | local파일없음·sharedJSON두파일·INFO/WARN/ERROR분기·100MB/날짜rollover/압축/30일·cap |
| LG02 | 병렬trace/사용자/MDC분리·error/async중복없음·신뢰IP/위조헤더·제어문자 |
| LG03 | local동적SQL줄바꿈/?/mapper시간·sharedSQL없음·비밀번호/PII/bind/cause표식미노출 |
| AU01 | REQUIRED조회STARTED/종료·이력실패503및본문차단·BEST_EFFORT실패기록·거부사건 |
| AU02 | 업무rollback후실패이력유지·민감변경감사같은commit·UNKNOWN·pool대기상한 |
| AU03 | audit권한/자기조회1회·재귀없음·날짜범위·SELECT/INSERT만·자동삭제없음 |
| AT01 | 정상/미존재/비활성비밀번호login·Argon2·길이Unicode·키/kid/alg/iss/aud/typ/exp검증 |
| AT02 | 누락/만료/폐기sid·권한/데이터범위401/403/404·세션쿠키만보내면API401 |
| AT03 | refresh단회·동시CAS·사용한hash재사용sid폐기·응답유실시안전한재로그인 |
| AT04 | idle30m/absolute8h·refresh/백그라운드만으로연장안됨·병렬touch역행/부활없음 |
| AT05 | allow/deny-new/replace-old각2서버병렬login·만료제외·갱신은신규아님 |
| AT06 | A로그인→B API/refresh, A logout→B401, logout-all다른기기차단 |
| AT07 | 계정/비밀번호/역할/역할권한변경시영향사용자폐기·DB/Redis중간실패fence복구 |
| AT08 | Redis/JDBC각실행·JDBC에Redis설정없어도정상·장애503·fallback금지 |
| AT09 | CSRF로그인전/후·다중탭·누락/위조·Origin/Referer·cookieSecure/HttpOnly/host·no-store |
| AT10 | 계정5회/15분·IP30회/분·미존재TTL·병렬증가·success가IP기록삭제안함 |
| AT11 | prelogin off인상태에세션생성없음, on일때A세션쓰기→B복원·allowlist/byteaJSON·TTL |
| AT12 | 선택Spring Session테이블미생성/독립생성/비활성후삭제/재시작미참조·corehistory유지 |
| AT13 | 키재시작/A·B공유·회전/구키만료/개발키shared금지·이전snapshot자격부활방지 |
| FEI01 | local rewrite+실제쿠키·Orval생성/타입/빌드·단일/다중탭갱신·logout이전캐시제거 |
| FEI02 | 401/403/409/429/503분기·변경무한재전송없음·프론트decimal후속예제명시 |
| SEC01 | 기존V15/V22예외비노출(필터/세션/파싱/error/HTMLAccept/headers)회귀·합성표식검사 |

Spring Session의 허용된 합성 속성 A/B 왕복은 실제 외부 인증 검증을 대체하지 않는다. SAML·OIDC·agent는 도입할 때 요청 결합·SameSite·서명·issuer·state·nonce·PKCE·replay·A 시작/B 콜백·SLO 검증을 추가한다. 미도입 연동은 조건부 미실행으로 남긴다.

## 6. 구현 순서·소유권·완료

1. backend: 공통구조/설정/JSON/메시지/DDL·선택migration,컴파일·단위확인.
2. backend: 로그/필터/SQL·이력writer/조회·실패정책.
3. backend: auth store2종/키/로그인/refresh/권한/동시정책/공유세션옵션.
4. backend: 공개API/OpenAPI·README·JUnit,승인된격리DB/Redis검증.
5. front(후속지시때): 생성API/CSRF/갱신/오류·캐시/정밀숫자예제상세설계후구현.
6. reviewer: untracked포함독립리뷰→담당수정→재리뷰,연결/회귀/멀티서버증거대조.

backend/**는 백엔드, frontend/**는 프론트, infra/local/**는 Docker 담당이다. 루트 README·docs/implementation은 설계 담당이 조정한다. docs/common-rules는 이번에 변경하지 않으며 담당 범위 밖의 공개 파일을 임의 수정하지 않는다.

최종 보고는 설계·구현·실행 검증·미검증·운영 결정을 구분한다. ASVS 완전 준수·취약점0·모든 SSO 지원·운영 준비 완료라고 표현하지 않는다. 이번에는 설계 작성과 정합성 점검만 완료했고 작업자에게 구현 지시는 보내지 않았다.
