# 인증 확장·Redis 공유 세션 기반

작성일: 2026-10-22 / 상태: 기반 설계. 소스·Redis·다중 서버 시험 미실행.

## 확정 방향과 범위

사용자 요구는 Redis 도입과, 향후 인증 연동 시 공유 세션 등의 기반 누락 때문에 막히지 않는 구조다. 모든 SSO/SAML/Windows 인증을 지금 구현하지 않는다.

전체 목표의 공통 기반은 Spring Security + Spring Session Redis, 내부 사용자·권한 경계, 공통 세션 수명주기, 다중 서버 검증이다. [1차 세팅](initial-setup.md)에서는 Security·공유 세션·CSRF·쿠키·직렬화·기본 거부를 준비하고 실제 내부 계정/권한 모델·로그인·A/B 인증 시험은 해당 기능 단계에서 구현한다. SAML/OIDC/제품 agent는 실제 선택할 때만 활성화한다. SSO는 사용자 경험이고 SAML/OIDC는 연동 프로토콜이며, Windows 인증은 Kerberos/SPNEGO·인증 프록시·제품 agent 등 방식이 달라 제품 계약 확인이 필요하다.

## AUTH01 — 인증 수단과 업무 경계

업무 서비스는 내부 userId·권한·테넌트·인증 강도/시각을 사용한다. SAML NameID나 OIDC token·agent 전용 객체를 업무 코드와 Controller 전체에 전달하지 않는다.

외부 신원은 provider/issuer + 안정적인 subject를 내부 계정에 연결한다. 같은 이메일·사용자명만으로 자동 병합하지 않는다. provider가 변경되더라도 내부 userId는 유지하고, 연결/해제는 재인증·감사·충돌 처리 계약을 따른다.

Spring Security의 표준 provider/filter·인증 성공/실패·SecurityContext 저장 확장점을 재사용한다. 모든 프로토콜을 같은 login DTO에 강제로 넣거나 자체 범용 인증 프레임워크를 만들지 않는다. 활성 provider는 서버 설정의 명시적 목록이며 사용자 입력으로 임의 issuer·metadata·agent 주소를 조회하지 않는다.

## AUTH02 — 로그인 상태의 단일 저장소

브라우저 → 신뢰 진입점 → Spring A/B → 동일한 Redis 세션 저장소.

- 단일 서버 개발도 Spring Session Redis 경로를 사용한다. Redis 장애 시 로컬 메모리 세션으로 자동 fallback하지 않는다.
- 모든 서버가 동일한 세션 저장소·앱/환경 namespace·쿠키 정책·직렬화 계약을 사용한다. 서로 다른 앱이나 환경의 namespace는 분리한다.
- SecurityContext·세션 CSRF·인증 요청 상태가 실제 Spring Session 경계를 통과하는지 확인한다. Redis client 의존성만 추가하고 내장 HttpSession을 사용하는 상태는 실패다.
- 세션 목록/사용자별 강제 종료를 위해 RedisIndexedSessionRepository를 기본안으로 한다. 계정당 세션 상한은 repository 선택만으로 원자 보장이 되지 않으므로 별도 동시성 계약을 검증한다.
- principal index는 내부 userId로 통일한다. 로그아웃·계정 정지·권한 변경·전체 세션 폐기는 A/B 모두에서 같은 결과여야 한다.
- 기존 유휴 30분·절대 8시간·동시 3개 기본안을 유지한다. 저장소 TTL만으로 절대 만료를 대신하지 않는다.
- SecurityContext를 인증 성공 응답 전에 저장하고 session fixation 보호를 적용한다. 버전별 자동/명시 저장 방식과 필터 순서를 D01에서 확인한다.
- 로드밸런서의 sticky session 없이 시험한다. sticky 설정으로 공유 누락을 가리지 않는다.

Spring Session의 repository·사용자 인덱스·직렬화·namespace를 확인한 근거: [공식 Redis 구성](https://docs.spring.io/spring-session/reference/configuration/redis.html). 위 수명·구성 선택은 프로젝트 정책이다.

## AUTH03 — 로그인 완료 전 상태와 프로토콜 추가 조건

| 단계 | 지금 준비할 기반 | 실제 연동 시 검증 |
| --- | --- | --- |
| 인증 시작 | 서버에 저장한 correlation과 복귀 경로 allowlist, 미인증 세션 지원 | SAML AuthnRequest, OIDC state/nonce/PKCE 등 실제 객체 |
| 외부 인증 후 callback | A에서 저장한 상태를 B에서 읽을 수 있는 경계 | 요청·응답 결합·서명·issuer·수신자·시간·replay |
| 인증 성공 | 내부 사용자 매핑·세션 ID 교체·SecurityContext 저장·CSRF 재발급 | 인증 강도/시각·비활성 계정·테넌트 정책 |
| 실패·만료 | 안전한 오류·정리·재시작 경로 | 잘못된 correlation·재사용·다중 탭·중단 후 재시도 |
| 로그아웃 | 애플리케이션의 공통 세션 폐기 | IdP SLO/백채널·agent 재인증·자동 재로그인 여부 |

Spring SAML은 기본적으로 AuthnRequest를 HttpSession에 보관한다. 로그인 후 세션만 공유하고 이 객체가 저장/복원되지 않으면 callback이 실패할 수 있다. [공식 SAML 요청 저장](https://docs.spring.io/spring-security/reference/servlet/saml2/login/authentication-requests.html)

SAML POST callback은 외부 site에서 돌아오는 POST라 SameSite=Lax 쿠키가 전달되지 않을 수 있다. SAML 도입 시 binding·HTTPS 시험 환경·쿠키 정책을 함께 결정한다. 필요한 경우 검토된 SameSite=None; Secure 프로필 또는 안전한 별도 요청 결합 방식을 적용한다. 글로벌 CSRF 해제나 모든 쿠키 완화로 해결하지 않는다. [쿠키 SameSite](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie)

callback의 제한적 CSRF 예외는 프로토콜 검증을 대체하지 않는다. IdP initiated login은 기본 허용하지 않고, 필요 시 별도 보안 계약으로 연다. 인증 중간 상태의 만료·단회 소비·다중 탭 동작은 provider별로 명세하며 business API와 혼합하지 않는다.

Windows agent/pre-auth 연동은 서버가 신뢰한 프록시/agent 검증을 전제로 한다. 외부 사용자가 보낸 사용자명·role 헤더만으로 인증하지 않는다. 직접 backend 접근 차단, 헤더 제거/재설정, 검증된 발급자·서명/채널·replay 통제를 제품별로 고정한다. [Spring Pre-authentication](https://docs.spring.io/spring-security/reference/servlet/authentication/preauth.html)

## AUTH04 — 직렬화·배포 호환

세션에 entity·열린 connection·전체 IdP 응답·불필요한 token을 저장하지 않는다. 내부 principal은 작은 버전 있는 모델로 관리한다. Spring Security/Session 및 추가 provider가 저장하는 객체 목록을 만든다.

직렬화는 지원 serializer와 Security 모듈·명시적 허용 타입으로 설계한다. 무제한 polymorphic type 수용·임의 class 역직렬화 금지. “JSON으로 바꾸면 끝” 또는 “공유 Redis라 모두 복원 가능”이라고 가정하지 않는다. SAML 요청 객체 등은 실제 버전별 round-trip 검증 후 적용한다.

rolling deploy 중 구/신 버전이 서로 저장한 객체를 읽을 수 있어야 한다. 호환되지 않는 변경은 세션 만료/재로그인 정책과 배포 순서를 명시한다. 임의 deserialization fallback으로 허용 범위를 넓히지 않는다.

## AUTH05 — Redis 설정·보안·장애

| 항목 | 기본 계약 |
| --- | --- |
| 라이브러리 | Spring Session Data Redis + Spring Data Redis, Boot BOM 호환 조합 |
| local | starter-project의 redis, 127.0.0.1:26379 → container 6379, 공개 테스트 사용자 testuser/1234. 실제 환경 최소권한 정책과 구분 |
| 운영 | private endpoint, ACL 전용 사용자·key/channel/명령 최소권한, TLS·인증서 검증 |
| namespace | 앱·환경별 고정 prefix, 서버 인스턴스 ID를 prefix에 넣지 않음 |
| 시작 | 필수 연결·인증·권한·쓰기/읽기 실패 시 ready=false. 실제 session read/write 검사 |
| 요청 장애 | 503 등 안전한 공통 실패, 무인증 허용/로컬 저장소 fallback 금지, 무한 retry 금지 |
| 메모리 | 세션용은 maxmemory + noeviction 기본안, 포화 시 실패·경보. 일반 cache와 별도 운영 인스턴스 권장 |
| 데이터 수명 | TTL·정리·폐기 기준, key expiry/event 설정과 인덱스 정합성 점검 |
| local 보존 | named volume + AOF everysec 기본안, 중지는 삭제하지 않음 |
| 운영 HA | topology·복제·영속화·RPO와 복구 후 재로그인/폐기 정책을 D06에서 확정 |

ACL은 선택한 indexed repository가 요구하는 key/channel·script·event 작업을 실제로 검사해 고정한다. 앱에 CONFIG·FLUSHALL 등 관리 권한을 포괄 부여하지 않는다. 필요한 keyspace notification 설정은 운영 provision에서 처리한다. Redis 논리 DB 번호는 별도 인스턴스와 같은 자원·장애 격리가 아니다.

AOF는 무손실 보장이 아니며 everysec 설정은 장애 때 최근 쓰기를 잃을 수 있다. 오래된 snapshot/replica로 복구하면 폐기된 세션이 되살아날 위험도 고려한다. 복구·불확실한 failover 시 관련 세션 namespace 폐기/세대 변경 후 재로그인하는 정책과 권한 철회 보존을 명시한다. 복구 작업은 승인된 대상에만 수행한다.

근거: [Redis 보안](https://redis.io/docs/latest/operate/oss_and_stack/management/security/), [Redis 영속성](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/). 연결 timeout·pool·메모리 수치는 배포 용량과 실제 버전을 정한 뒤 고정한다.

## AUTH06 — 완료 검증

| 시험 | 조건 | 통과 기준 |
| --- | --- | --- |
| AF01 | A에서 로그인, 다음 요청을 B로 강제 | 같은 내부 주체·권한, sticky 불필요 |
| AF02 | A에서 CSRF 발급 후 B에서 변경 | 정상 token 통과, 누락/위조는 거부 |
| AF03 | A에서 인증 시작 후 B에서 callback | 선택 provider의 요청 결합·복원·단회 처리 성공. 미도입 provider는 경로 미공개 |
| AF04 | A에서 logout/권한 철회·계정 정지 후 B 재요청 | 폐기·갱신 반영, 세션 상한 병렬 시험 통과 |
| AF05 | A 재시작·신구 버전 혼재 | 공유 상태 유지 또는 명시한 재로그인, 허용 타입만 복원 |
| AF06 | Redis 차단·오류·메모리 포화·복구 | 안전한 실패·경보, fallback 없음, 폐기 자격 부활 방지 |
| AF07 | SAML cross-site POST·실제 HTTPS 프록시 | 선택 binding·쿠키 전달·외부 주소·시간·키 일치. provider 도입 시 실행 |
| AF08 | agent 헤더 위조·직접 backend 호출 | 인증 우회 불가. agent 도입 시 실행 |

AF01/AF02/AF04~AF06은 공통 기반 완료 기준, AF03/AF07/AF08은 해당 연동 추가 시 필수다. 현재 모두 미실행이다. PostgreSQL·Redis·앱 A/B를 포함한 격리 검증 환경을 준비하되 실제 실행은 별도 구현·실행 범위에서 진행한다.

## 문서 연결

[결정 관문](decisions.md), [기술 구성](technology-stack.md), [로컬 환경](local-environment.md), [API 세션 계약](security/backend.md), [보완 통제](security/controls.md)를 함께 적용한다. 이번 결정은 공유 기반을 기본으로 올리는 것이며 SAML/OIDC/Windows agent 전부의 구현·설치 승인이 아니다.

## Docker 범위

Redis·DB 실행에 Docker를 쓰는 것은 로컬 편의 선택이다. 제품 코드는 접속 설정만 사용하고 Docker 설치·Compose·socket·volume을 참조하지 않는다. migration·ACL provision·배포는 환경별 실행 도구로 수행하며 원본 정책을 공유한다. Docker 없는 외부 DB/Redis 환경의 실행 검증은 필수다. 상세는 [로컬 환경 이식 계약](local-environment.md)을 따른다.
