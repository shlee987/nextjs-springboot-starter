# JWT 인증·공유 상태·외부 인증 확장 설계

기준일: 2026-10-30 / 버전: 1.1 / 상태: 자체 로그인·JWT·Redis/JDBC·선택 Session 기반 구현 및 로컬 검증 완료. 외부 SAML/OIDC는 확장 지점만 제공한다. [현재 확인 범위](foundation-status.md), [전체 설계](backend-foundation.md)를 함께 읽는다.

## AUTH01 — 책임과 구성 (3-1)

일반 API는 JWT, 로그인 진행 중 필요한 임시 상태는 조건부 Spring Session이다. JWT라고 HttpSession을 전역 금지하거나 HttpSession SecurityContext만으로 API를 인증하지 않는다. 자체 비밀번호 로그인부터 구현하고 실제 SAML/OIDC/Windows·agent는 미도입한다.

인증 수단 → 내부 사용자 연결 → 활성 상태/권한 확인 → 공통 AuthLoginService → JWT/갱신 발급·이력. 업무에는 내부 userId/권한/AuthContext만 전달한다. 외부 신원은 provider/issuer+불변 subject로 연결, 이메일 자동 병합 금지. 제품별 표준 Spring AuthenticationProvider/필터/성공처리 확장점을 사용하고 범용 인증 엔진을 새로 만들지 않는다.

필요 의존성: 기존 Spring Security에 BOM 관리 oauth2-resource-server·oauth2-jose, Spring Session JDBC를 추가할 계획이다. Boot 해당 starter 좌표/lock은 구현 시 확인한다. NimbusJwtEncoder/JwtDecoder를 재사용하고 JJWT 등 별도 JWT 라이브러리/authorization server는 추가하지 않는다. 비밀번호는 기존 보안 계약 Argon2id(m=19456KiB,t=2,p=1,salt16,hash32)를 DelegatingPasswordEncoder의 식별자와 저장한다. 지원 provider 의존성은 Boot 관리 Bouncy Castle 범위만 추가·lock하고 실제 API/성능 검증한다. 무조건 BCrypt로 바꾸지 않는다.

## AUTH02 — 사용자·조직·권한 (3-2~3-5)

[데이터 모델](backend-data-model.md)의 APP_USER/LOCAL_CREDENTIAL/EXTERNAL_IDENTITY/DEPARTMENT/USER_DEPARTMENT/APP_ROLE/APP_PERMISSION/USER_ROLE/ROLE_PERMISSION을 사용한다.

- loginId: 소문자 ASCII [a-z0-9][a-z0-9._-]{2,49}, 입력도 같은 규칙. 자동 trim·대소문자 변환 없음. 내부 ID는 bigint, API 문자열. 이름1~100, 이메일은 선택254자/형식 검증(로그인 ID 아님), 중복 이메일을 외부 신원 연결 근거로 삼지 않음.
- 자체 비밀번호15~128 Unicode code point, trim/정규화/절단/조합 강제/주기 만료 없음. local 예제도 이 길이를 지킨다. 미존재 사용자 dummy hash 비교·통일된 LOGIN_FAILED, 비활성·없는 계정 구분 노출 없음.
- 계정 ACTIVE/INACTIVE. 부서 부모 관계·복수 소속·대표 하나(부분 UNIQUE), 소속 없음 허용. 부서 소속은 권한 자동 부여 아님.
- 사용자→역할→권한. 사용자 직접 권한·역할 상속·관리자 전체 우회 없음. 권한은 등록된 코드 허용목록이며 배포 SQL이 관리, 역할 구성은 관리 업무에서 변경.
- URL의 로그인 조건 + Service @PreAuthorize 권한 + SQL 데이터 범위. 요청 actorId/role/departmentId로 인증 주체를 바꾸지 않는다.
- USER 기본 USER_READ_SELF, DEPARTMENT_READ. ADMIN은 이번 등록 권한 전부 명시 부여. 권한 코드: USER_READ_SELF, USER_READ, PERSONAL_INFO_READ, USER_UPDATE, DEPARTMENT_READ, DEPARTMENT_UPDATE, ROLE_READ, ROLE_ASSIGN, AUDIT_READ, SECURITY_AUDIT_READ. 상세 관리 API/화면은 별도 기능, 서비스 계약과 seed·기본 조회만 이번 구현.

## AUTH03 — 토큰과 인증 상태 (3-8~3-9)

Access JWT: RS256, RSA>=3072bit, kid 필수, typ=at+jwt, iss/aud/sub(내부ID)/sid(로그인UUID)/jti(UUID)/iat/nbf/exp/auth_time. 역할·개인정보는 넣지 않고 공유 AuthLogin의 권한 snapshot을 사용한다. Nimbus decoder는 허용 알고리즘 RS256 고정·서명·typ/iss/aud·시간·필수 claim 형식·jti/sid 검증. JWT header의 jku/x5u/jwk를 원격 조회하지 않는다. 서명만 정상이어도 상태가 없거나 폐기/차단/만료면 거부한다. 검증 완료된 JWT의 sid/sub와 저장 주체가 일치해야 하며, 커스텀 Authentication converter는 저장소의 permissionsSnapshot만 GrantedAuthority로 변환한다. scope 기본 converter가 권한을 자동 채운다고 가정하지 않는다.

Access TTL 10분, exp는 절대 만료를 넘지 않음. Refresh는 CSPRNG32byte 이상 base64url 불투명 난수. SHA-256 해시만 인증 상태 저장소에 보관, 원문은 발급 응답 Set-Cookie 이외 로그/본문/영속 DB에 저장하지 않는다. 해시 조회 시 원문 노출/비교 timing 누출 방지. 매 갱신 회전, 과거 hash는 해당 로그인 절대 만료까지 tombstone으로 유지해 재사용 탐지.

AuthLogin: sid,userId,createdAt,authenticatedAt,lastActivityAt,idleExpiresAt,absoluteExpiresAt,revokedAt,reason,permissionsSnapshot,currentRefreshHash. Redis/JDBC가 동일 의미를 구현한다. JWT가 있는 모든 보호 요청은 이 상태와 사용자 차단 상태를 확인한다. 캐시로 폐기 반영을 지연하거나 토큰 부재 시 DB 사용자만 보고 자동 상태 재생성하지 않는다.

미사용30분/절대8시간, 서버 Clock UTC 밀리초. 인증된 업무 API와 GET me 조회를 활동으로 취급, HEAD me/csrf/refresh/자동 상태 폴링은 활동 연장 제외. me를 상태 폴링으로 사용하지 않는다. lastActivity와 idleExpiresAt 갱신은 MAX로 단조 증가하고 절대시각을 넘지 않는다. 동작 완료 후 오래 걸린 요청이 만료 상태를 부활시키지 않는다.

### 로그인·갱신·재사용

1. Origin/CSRF·로그인 한도를 확인 → DB 사용자/해시 확인. 허용되지 않은 경우 일반 실패·보안 이력.
2. 사용자별 원자 처리 경계에서 최신 계정 상태/변경 fence를 확인하고 동시 로그인 정책을 적용 → AuthLogin/refresh hash 생성. 해시 계산은 이 잠금 밖에서 수행하고 발급 직전 재확인.
3. 인증 상태 영속 확인 후에만 쿠키를 응답. 저장 실패는503, 부분 실패로 남은 로그인은 즉시 폐기. 실제 새 쿠키가 전달됐는지는 서버가 보장하지 않음.
4. refresh는 refresh 쿠키+Origin/CSRF 검증, 만료/차단/폐기 확인, current hash 단회 CAS 교체와 tombstone 추가를 한 원자 연산으로 수행. 서명 키 교체·절대 만료는 존중.
5. 사용한 hash 재등장은 해당 sid 전체 폐기·TOKEN_REFRESH_REUSED 401. 모르는 무작위 hash는401이고 임의 사용자 sid를 폐기하지 않음. 한 사용자의 다른 정상 sid는 유지.
6. 정상 브라우저의 동시 갱신도 재사용으로 판정될 수 있다. 프론트는 탭 내 single-flight+탭 간 배타 실행 필수. 원문 refresh 저장·재발급 grace로 우회하지 않는다. 응답 유실 시 안전하게 재로그인하며 옛 refresh를 자동 반복하지 않는다.

### 키 수명주기

공유 환경은 외부 개인키·검증 공개키 목록을 주입한다. active kid 1개, 검증 중인 이전 공개키는 기존 access TTL+skew 동안 유지. 유출이면 이전 키를 즉시 제거·전체 관련 로그인 폐기. 서로 다른 키/issuer/audience/namespace 서버는 같은 서비스로 ready 금지. local DB 개발 키는 재시작/여러 인스턴스 공유 용도일 뿐 운영 경로 아님.

## AUTH04 — 저장소·동시 로그인·장애 (3-9, 사용자 추가)

기본 redis, 대안 jdbc. 앱 시작 중 한 구현만 선택하고 runtime fallback·이중 쓰기를 하지 않는다. 저장소 전환은 기존 로그인을 폐기하고 재로그인, 자동 데이터 이관 없음. 사용자별 목록 조회·폐기·상태확인·한도·refresh CAS는 AuthStateStore의 동작 계약이며 비즈니스 서비스에 redis/jdbc 분기를 흩뿌리지 않는다.

| concurrent-login | 원자 동작 |
| --- | --- |
| allow(기본) | 기존 유효 로그인 유지·새 sid 생성. 임의 3개 상한 도입 금지 |
| deny-new | 유효 로그인 있으면409 CONCURRENT_LOGIN_DENIED. 기존 것 유지 |
| replace-old | 기존 sid 전부 폐기하고 새 sid 생성, 다른 서버 다음 요청부터 거부 |

제한 모드는 사용자당1개. 만료/폐기된 로그인 제외. refresh는 새 로그인 아님. 성공한 자격 확인 뒤에만 동시 로그인 여부 응답. 정책을 allow에서 제한으로 바꿀 때 기존 로그인 자동 정리는 하지 않고 다음 신규 로그인부터 위 동작 적용; 즉시 강제 적용은 명시적 전체 로그아웃 절차. 모든 노드의 정책 digest가 다르면 준비 실패.

- Redis: 앱·환경 namespace, user 단위 공통 hash tag로 관련 key를 묶고 Lua로 check+expire cleanup+정책+추가/CAS/폐기를 원자 처리. Redis Cluster에서도 cross-slot 금지. 트랜잭션과 같은 의미라고 임의의 get→set으로 대체하지 않음. 해시 기반 로그인/refresh locator를 두되 locator만으로 인증하지 않고 최종 user 원자 상태를 검사. locator의 지연/유실은 거부로 처리.
- JDBC: AUTH_USER_GUARD 한 행을 SELECT FOR UPDATE하여 사용자별 로그인·갱신·폐기를 직렬화. sid/hash UNIQUE. 만료 체크/정리와 정책 적용·생성은 같은 짧은 트랜잭션. JVM synchronized나 Spring Session 인덱스만으로 동시 제한을 보장하지 않음.
- login index/refresh history/한도 기록은 TTL 또는 제한 batch 정리, 만료 판정은 정리 job 실행 여부에 의존하지 않음. Redis noeviction, 명령/ACL/TLS 최소권한, 연결/응답 시간 제한과503. JDBC에서 Redis 연결/readiness 사용 금지.
- Redis/JDBC 상태 손실·오래된 백업 복구 시 폐기 토큰 부활 방지: 인증 namespace 세대 변경 및 전체 재로그인, 이전 키/세대 차단. 복구 운영절차 없이 기존 snapshot을 그대로 서비스 재개하지 않음.

## AUTH05 — 변경·폐기와 DB/Redis 정합성 (3-11)

현재 logout은 sid, logout-all은 user 전체, 계정 비활성/비밀번호 변경/역할 변경은 영향 사용자 전부 폐기하고 재로그인 요구. 공통 AuthInvalidationService만 사용하고 SQL만 직접 변경하는 경로는 제공하지 않는다. 이미 권한 검사를 통과한 실행 중 요청은 별도 취소하지 않음.

- JDBC: 계정/권한 변경+영향 AuthLogin 폐기+필수 변경 이력을 하나의 DB 트랜잭션에 저장. 공통 user guard/역할 잠금 순서로 로그인과 경쟁을 직렬화한다.
- Redis: DB와 Redis의 원자 commit을 가정하지 않는다. 변경 전에 영향 사용자에 영속 BLOCKED fence를 설정하고 해당 사용자 로그인/refresh/API를 거부 → DB 변경·필수 변경 이력 commit → 기존 로그인 전부 폐기 확인 → fence 해제. 중간 실패는 fence를 남겨 안전하게 차단, DB rollback이어도 폐기된 로그인 복원 금지.
- fence는 만료로 자동 해제하지 않음. 변경 operationId로 소유 확인·중복 변경 거부. DB 사용자/역할 잠금 후 영향 사용자 ID 오름차순으로 설정, 역할 할당도 같은 역할 잠금 규칙을 사용하여 누락 방지. 성공 응답은 모든 영향 사용자 폐기 확인 후에만 전송.
- crash/DB commit 결과 불명확/Redis failure는503 및 정제된 복구 식별자. 구현자는 runbook에 DB commit 상태 확인→대상 전체 폐기 재수행→소유 fence 해제 절차를 제공. 일반 캐시/메시지 브로커/자동 outbox 플랫폼을 이번 범위에 추가하지 않는다. Redis 장애 상태에서 권한 변경만 성공 처리하지 않음.
- 정상화 후 첫 신규 로그인은 항상 DB의 현재 계정/권한을 다시 읽음. 기존 snapshot을 재사용해 권한을 복원하지 않음.

## AUTH06 — 쿠키·CSRF·필터 (3-6,3-12~3-14)

| 쿠키 | 공유 환경 | local |
| --- | --- | --- |
| Access | __Host-access, HttpOnly/Secure/Lax, Path=/, Domain 없음 | local-access, HttpOnly/Lax, Path=/, loopback HTTP만 Secure 예외 |
| Refresh | __Host-refresh, 같은 속성 | local-refresh |
| CSRF | __Host-csrf, 같은 속성, API로 token 전달 | local-csrf |
| 조건부 임시 세션 | __Host-prelogin, 기본Lax, provider별 재검토 | local-prelogin |

Access/Refresh 쿠키의 Max-Age는 로그인 절대 만료까지 남은 시간으로 제한한다. Access JWT exp(10분)와 쿠키 수명은 다르며, 만료 JWT도 쿠키에 남아 서버가 TOKEN_EXPIRED로 식별하고 갱신할 수 있게 한다. 쿠키 수명이 JWT 검증을 연장하지 않는다. API의401은 쿠키를 일괄 삭제하지 않으며, 명시 logout/인증 전환 경계에서만 정리해 다른 탭의 최신 로그인을 지우지 않는다. CSRF 쿠키 Max-Age8h, prelogin은10m 이내다. 장기 remember-me는 미제공이다.

쿠키 인증은 CSRF 유지. 일반 API는 Spring CookieCsrfTokenRepository 사용(세션 생성 방지), cookie HttpOnly=true, GET csrf가 실제 token을 materialize하고 XOR/BREACH 표현과 resolver를 일치시켜 token/headerName을 반환. 저장 토큰과 공개 표현을 혼용하지 않음. CSRF 쿠키는 로그인/로그아웃 때 교체·프론트 재조회, refresh에는 매번 교체하지 않음. 쿠키명/Domain/Secure를 런타임 검사하고 제3자 subdomain에서 덮어쓸 수 없도록 __Host 제약 유지.

상태 변경의 Origin은 APP_PUBLIC_ORIGIN 정확 일치. Origin 없으면 Referer의 origin 정확 일치, 둘 다 없거나 null은403 ORIGIN_REJECTED. CLI/Swagger 예제도 이 규칙 준수. CORS는 같은 출처 기본 빈 목록, wildcard 금지. CSRF 발급 no-store, 발급남용 IP 제한. 로그아웃/인증 실패 쿠키삭제는 이름·Path·Domain을 발급과 일치시킨다. access 만료401에서는 refresh쿠키를 자동 삭제하지 않음.

필터 순서 계약: trace/MDC·안전 오류 경계(세션필터보다 앞) → 조건부 Spring Session → 선택 chain의 보안 헤더/CORS·Origin·CSRF → JWT 검증/상태확인 또는 표준 로그인 인증 → 권한 → MVC·업무/감사. Spring 기본 순서를 임의 복제하지 말고 각 필터 위치·error dispatch·예외 포착을 실제 chain에서 검증한다. 서블릿 오류·401/403/413/503도 공통 응답/헤더를 유지. body/trace 최종 기록은 한 번, MDC finally 제거.

API chain /api/**는 SecurityContext request 범위만 사용, 세션/RequestCache 인증 승계 금지. login/refresh/logout/CSRF는 정확 메서드별로 JWT 선행검증을 제외하여 만료 access가 갱신/로그인 방해하지 않게 한다. logout은 refresh로도 현재 sid를 안전하게 찾아 폐기, 무효/없는 쿠키는204. logout-all·비밀번호 변경은 유효 access 필요. 세션 쿠키만 보낸 API 요청은401이어야 한다.

별도 외부 인증 chain은 기능 활성 때만 등록, /auth/external/**의 IF_REQUIRED HttpSession과 표준 프레임워크 사용. global STATELESS 설정을 강제하지 않음. 인증 성공은 공통 발급 후 임시 세션 정리, HttpSession 인증이 일반 API로 새지 않음. Basic/form 기본 페이지·자동 생성 사용자·URL 세션 ID 금지.

## AUTH07 — 외부 로그인 진행 상태와 직렬화 (3-10)

Spring Session은 외부 연동 전까지 enabled=false. RedisIndexedSessionRepository 또는 JdbcIndexedSessionRepository를 하나만 조건부 구성, 일반 API JWT와 별개. enabled=true면 선택 저장소/필터·serialization/readiness 실제 확인. 세션에 user entity·connection·원문 assertion·불필요한 외부 token을 저장하지 않음. JSON+명시 허용 타입, native Java serialization·광범위 default typing 금지. JDBC bytea도 검토된 JSON converter를 명시해 기본 Java 직렬화 사용을 막는다.

기본 prelogin 10분, 프로토콜 state/nonce/PKCE·요청 결합의 TTL/단회소비를 provider별로 고정. 단회 replay 표시는 공유 원자 저장소로 보장(동일 세션 경쟁 저장으로 복원되지 않음). rolling deploy serializer 호환성을 검증한다. SAML 요청 실제 타입은 provider 도입 시 허용·왕복 검증, 지금 모두 호환된다고 주장하지 않음.

SAML cross-site POST는 Lax 쿠키 누락 가능. 실제 binding 도입 시 HTTPS에서 별도 prelogin 쿠키 SameSite=None;Secure 또는 검토된 결합 방식을 선택한다. API Access/Refresh까지 일괄 완화하지 않고 CSRF 예외는 검증된 callback에 한정. OIDC는 state/nonce/PKCE, agent는 검증 발급자/채널·위조 헤더 차단·backend 직접접근 통제를 추가한다. 앱 logout과 IdP 전체 logout은 별도이며 외부 logout 전파 미구현 상태를 명시한다.

## AUTH08 — 남용·오류·이력 (3-13~3-15)

실패한 자격 검증 계정별 15분/5회, 다섯 번째 실패 후15분 제한, 제한 요청429/Retry-After. IP별 30회/1분은 로그인 시도 전체에 적용. 계정 키는 정규식으로 제한한 loginId의 SHA-256, 원문 IP/아이디를 key/log로 출력하지 않음. 미존재 계정도 제한하되 요청 길이·IP 선행 제한·TTL15분으로 무한 관리데이터 증가 완화. 성공은 해당 계정 실패만 초기화하고 IP 전체 한도는 유지. Redis Lua/JDBC 원자 update, fixed window 경계 포함 검증. 영구잠금·sleep thread 점유 없음. 일반 요청 한도 기존 API05 유지.

401: LOGIN_FAILED, UNAUTHENTICATED, TOKEN_EXPIRED, TOKEN_REVOKED, TOKEN_REFRESH_REUSED. 403: ACCESS_DENIED/CSRF_TOKEN_MISSING/CSRF_TOKEN_INVALID/ORIGIN_REJECTED. 409: CONCURRENT_LOGIN_DENIED. 429: RATE_LIMITED. 503: AUTH_STATE_UNAVAILABLE. 상세 code는 api-contract 표를 따른다. 모든 인증 API no-store, raw token은 JSON에 넣지 않는다.

로그인/실패/제한/로그아웃/재사용/권한 변경/저장소 장애는 보안 이력. 이력 쓰기가 실패하면 새 토큰 발급·refresh는503, 기존 인증 상태 폐기. logout은 이력 실패에도 폐기를 먼저 유지하고503/정제 ERROR 기록, 이미 폐기한 토큰을 복원하지 않음. 일반 실패 이력 저장 불가도 성공으로 바꾸지 않고503. 계정·권한 변경 감사는 해당 DB 변경과 원자 저장.

## 공식 근거와 확인 범위

2026-10-29 다음 공식 문서의 관련 절을 확인했다. Spring Security7.1.2/Session4.1.1 기준, 정책 숫자·store/fence·API는 프로젝트 설계다. API 메서드/호환 동작은 실제 구현 시 컴파일·왕복 검증해야 한다.

- [Spring JWT 검증·의존성](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Spring CSRF·쿠키 저장소](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Spring Session JDBC·JSON·사용자 SQL](https://docs.spring.io/spring-session/reference/configuration/jdbc.html)
- [Spring Session Redis·직렬화](https://docs.spring.io/spring-session/reference/configuration/redis.html)
- [SAML 요청의 기본 HttpSession 저장](https://docs.spring.io/spring-security/reference/servlet/saml2/login/authentication-requests.html)
- [JWT 검증 BCP](https://www.rfc-editor.org/rfc/rfc8725.html), [갱신 토큰 회전·재사용](https://www.rfc-editor.org/rfc/rfc9700.html)
- [비밀번호 해시](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)

이번 작업에서 로그인/Redis/JDBC/SSO/키 생성/DB 쓰기는 실행하지 않았다. 상세 시험은 [인계와 검증](backend-handoff.md).


## 쿠키 JWT와 CSRF 기본 예외의 상호작용 (2026-10-30 마감 보완)

실제 연동에서 Resource Server의 기본 Bearer 요청 CSRF 예외가 쿠키 토큰 추출에도 적용되는 문제를 발견했다. 쿠키 기반 인증에서는 이 예외를 사용하지 않는다. 최종 구성된 CsrfFilter는 안전 메서드를 제외한 요청에 CSRF를 요구해야 하며, Origin 검사만으로 이를 대신하지 않는다. 쿠키 access가 있는 POST/PUT/PATCH/DELETE의 누락·잘못된 CSRF 거부와 정상 토큰 성공, 공개 로그인/갱신/로그아웃 및 GET/HEAD 회귀를 함께 확인한다. 확인 결과와 미확인은 백엔드 검증표를 따른다.

사용 버전7.1.2의 [Resource Server 구성 원본](https://github.com/spring-projects/spring-security/blob/7.1.2/config/src/main/java/org/springframework/security/config/annotation/web/configurers/oauth2/server/resource/OAuth2ResourceServerConfigurer.java)의 기본 CSRF 예외 등록을 확인했다. 기본 동작을 쿠키 인증의 안전 보장으로 가정하지 않고 실제 완성된 필터 체인을 검증한다.
