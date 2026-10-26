# 본문 대조로 보완한 공통 통제

문서 버전 0.2 / 검토일 2026-10-22. 기존 영역별 설계와 함께 적용한다. 이번 문서는 제품 미선택 상태에서도 정할 수 있는 구현 계약이다. 수치·운영 일정은 프로젝트 기본안이며 공식 표준의 수치라고 주장하지 않는다.

[ASVS 조항 대조표](asvs-mapping.md)는 5.0.0의 각 조항을 이 문서와 기존 설계·검증으로 연결한다. L3 전체를 기본 범위에 추가하는 것은 아니다. 상태는 문서 검토 완료이며 구현·시험 통과가 아니다.

## GOV01 — 입력 해석·파라미터·실행 경계

- 프로토콜에서 요구한 decode·정규화를 검증 전에 한 번 수행하고, 검증 후 다른 표현으로 재해석하지 않는다. 저장은 원문 의미를 유지하고 출력 시 사용 문맥에서 encoding한다.
- 같은 scalar query/header/JSON key가 중복되면 400 기본안. 배열은 명세한 필드에서만 허용한다. query/body/path의 같은 이름 중 무엇을 우선할지 framework에 맡기지 않는다.
- 외부 JSON을 설정 객체·prototype·권한 객체에 deep merge하지 않는다. __proto__·constructor·prototype 경로를 사용자 입력으로 수정할 수 없게 한다. 필요한 dictionary는 Map 또는 안전한 자료구조를 사용한다.
- SQL·URL·HTTP header·로그·HTML은 각각의 안전 API를 사용한다. HTML escaping 하나를 다른 문맥의 방어로 재사용하지 않는다.
- 사용자 입력 기반 eval·SpEL·템플릿·OS 명령 실행은 기본 미사용. 명령 실행이 필요하면 고정 실행 파일과 인자 배열, 최소권한·크기/시간 제한을 별도 설계한다. shell 문자열 결합 금지.
- LDAP·XPath·JNDI·LaTeX·memcache·메일 명령·XML은 미도입 조건부다. 도입 시 해당 인터프리터의 안전 API·제한과 회귀 시험을 먼저 추가한다. XML은 DTD·외부 entity·외부 schema 접근을 비활성화한다.
- regex는 사용자 패턴 직접 실행을 금지하고 입력 길이·실행 시간 또는 안전한 엔진으로 자원 소비를 제한한다. 정수 범위·곱셈/합산 overflow·파일 handle 해제를 검토한다.
- CSV/스프레드시트 export 도입 시 formula injection과 대상 앱 재해석까지 검사한다. CSV quoting만으로 수식 실행 방지가 되었다고 판단하지 않는다.

관련: ASVS V1, V15.3 / 검증 V13, V23, V28. 원문: [V1](https://github.com/OWASP/ASVS/blob/v5.0.0/5.0/en/0x10-V1-Encoding-and-Sanitization.md), [V15](https://github.com/OWASP/ASVS/blob/v5.0.0/5.0/en/0x24-V15-Secure-Coding-and-Architecture.md).

## GOV02 — 필드 단위 권한·전달 주체

권한표는 함수·레코드·필드의 읽기/쓰기·테넌트·업무 상태를 구분한다. 역할만 맞으면 응답 전체를 주지 않는다. DTO allowlist로 최소 필드를 반환하고, 클라이언트가 role·tenant·소유자·승인 상태를 임의 변경할 수 없게 한다.

입력에 명세 밖 필드가 있으면 400. 명세된 필드라도 해당 주체가 쓸 권한이 없으면 403 기본안이며 무시하고 성공시키지 않는다. 서버에서 파생하는 필드는 요청 모델에서 제외한다. API별 존재 은닉 정책과 오류코드를 확정한다.

서비스가 다른 서비스를 대신 호출해도 원래 사용자의 권한을 초과하지 않는다. machine 계정으로 전달할 때도 검증된 원래 주체·권한 문맥을 유지하고, 외부 X-User-ID 헤더를 신뢰하지 않는다. 권한/계정 상태 변경은 다음 보호 요청부터 반영되도록 세션·권한 캐시를 무효화한다.

관련: ASVS V8 / 검증 V03, V24. 원문: [V8](https://github.com/OWASP/ASVS/blob/v5.0.0/5.0/en/0x17-V8-Authorization.md).

## GOV03 — 업무 순서·동시성·재실행

변경 API는 허용 상태 전이, 선행 단계, 소유권, 개별/전역 한도, 함께 성공해야 하는 변경을 명세한다. 검증과 변경 사이에 다른 요청이 끼어도 조건이 유지되도록 DB 제약·조건부 update·버전 검사·잠금 중 실제 계약에 맞는 방식을 선택한다.

잔여 재고 1개에 동시 요청 2개라면 성공은 최대 1개여야 한다. 충돌은 409 기본안, 부분 성공 계약이 없으면 전체 rollback. 외부 부수 효과가 있으면 transaction만으로 되돌릴 수 있다고 가정하지 않고 outbox 또는 보상·재실행 계약을 별도 설계한다.

반복 제출 가능한 고위험 변경에는 주체·동작·payload에 묶은 idempotency key와 보존기간·중복 응답 계약을 정한다. 키 자체가 권한을 부여하지 않는다. 대량 조회/내보내기·비싼 암호 연산은 사용자별·전체 동시성 상한과 큐 상한을 정한다.

관련: ASVS V2, V15.4 / 검증 V25. 원문: [V2](https://github.com/OWASP/ASVS/blob/v5.0.0/5.0/en/0x11-V2-Validation-and-Business-Logic.md).

## GOV04 — 세션 관리 보완

P1 기본안의 동시 세션은 계정당 3개, 초과 시 가장 오래된 세션을 서버에서 폐기하고 새 로그인을 허용한다. 임계값 판단·추가는 원자적으로 수행한다. 정책 변경 시 D04와 검증을 함께 갱신한다.

사용자는 재인증 후 활성 세션 목록에서 시각·대략적인 기기·현재 세션 여부를 보고 다른 세션 또는 전체 세션을 종료할 수 있어야 한다. raw 세션 ID·토큰은 UI에 노출하지 않는다. 관리자는 별도 권한·재인증·감사 조건으로 특정 사용자/전체 세션을 폐기할 수 있어야 한다.

로그아웃은 모든 보호 화면에서 접근 가능해야 한다. 비밀번호·인증요소 변경 후 다른 세션 종료 수단을 제공하고, 계정 정지·삭제는 전체 세션 폐기. 절대 만료 기준시각은 서버에서 관리하고 단순 활동으로 연장하지 않는다. SSO를 채택하면 IdP/RP 만료·로그아웃·재인증 전파를 따로 확정한다.

관련: ASVS V7 / 검증 V04, V26. 원문: [V7](https://github.com/OWASP/ASVS/blob/v5.0.0/5.0/en/0x16-V7-Session-Management.md).

## GOV05 — 인증 강도·복구의 최소 계약

ASVS 5.0.0 6.3.3은 L2에서 복수 인증요소/수단을 요구한다. 관리자만 MFA인 기존 기본안이나 일반 사용자의 비밀번호 단독 로그인은 L2 전체 충족으로 표시할 수 없다. D04에서 전체 사용자 인증 강도를 정한다. 비밀번호 단독 프로필을 채택하면 L2 차이를 명시하고 준수 선언을 금지한다.

자체 비밀번호 방식이면 변경 기능은 필수이며 현재 비밀번호·새 비밀번호·최근 인증을 요구한다. 비밀번호 입력 type=password, 붙여넣기/비밀번호 관리자 허용, 원문 변형 금지, 최소 흔한 비밀번호 3000개와 유출 목록·프로젝트/조직 맥락 단어를 차단하는 기준을 둔다.

초기 활성화·reset은 CSPRNG 32바이트 이상 단회 토큰 기본안, 서버에는 검증용 해시·주체·목적·만료만 저장, 유효시간 10분 기본안. 소비를 원자적으로 처리하고 재발급·재사용·만료 토큰을 거부한다. 비밀번호 힌트/비밀 질문 금지, 관리자가 사용자 비밀번호를 선택하는 방식 금지. reset은 등록 MFA를 우회하지 않으며 인증요소 변경을 사용자에게 통지한다.

MFA를 도입하면 TOTP seed·복구코드 보관, 단회 사용, 실패 제한, 신뢰 시간, 분실/재등록·폐기·계정 복구를 함께 설계한다. low-entropy 복구코드는 적응형 해시, 충분한 난수 토큰은 안전한 해시. out-of-band 인증은 원 요청에 결합하고 최대 10분, TOTP 시간 step 기본 30초·허용 창과 재사용 차단을 명세한다. SMS는 약한 수단임을 표시하고 더 강한 대안을 제공한다. 기법별 상세 API는 D04 해소 후 확정한다.

관련: ASVS V6 / 검증 V27. 원문: [V6](https://github.com/OWASP/ASVS/blob/v5.0.0/5.0/en/0x15-V6-Authentication.md).

## GOV06 — 암호화·난수·키 대장

키 대장에는 목적·소유자·알고리즘·강도·키 ID·저장 위치·허용 소비자·생성/만료/회전·폐기·재암호화 방법을 기록한다. 비밀 키 원문을 문서에 기록하지 않는다. 용도와 환경 간 키 재사용 금지, 관리형 vault/KMS 기본안, 서명키와 데이터 암호화키 분리.

보안 난수는 플랫폼 CSPRNG에서 최소 16바이트, 새 토큰 기본 32바이트. UUID의 128비트 표기나 base64 길이를 128비트 엔트로피로 간주하지 않는다. nonce/IV는 사용하는 암호 알고리즘의 별도 규칙을 적용한다.

업무 필드 암호화가 필요하면 검증된 라이브러리의 AEAD, 기본안 AES-256-GCM과 키별 유일한 96비트 nonce, 키 버전·nonce·tag를 포함한 포맷을 명세한다. tag 검증 실패는 안전하게 거부하고 원문 반환 금지. nonce 중복 위험·사용량 한도·회전은 KMS/암호 라이브러리와 함께 검증한다. 자체 암호 알고리즘·ECB·MD5/SHA-1 보안 용도·비밀번호의 단순 해시 금지.

서명/키 교환은 최소 128비트 보안 강도를 만족하는 지원 알고리즘, 충돌 저항 해시는 SHA-256 이상 기본안. 비밀번호 비용과 pepper, 데이터 재암호화·백업 복호화키 보존은 별도 수명주기로 관리한다. 필요한 암호 기능만 구현하고 “암호화가 없으니 TLS/세션/비밀번호 키도 해당 없음”으로 처리하지 않는다.

관련: ASVS V11 / 검증 V28. 원문: [V11](https://github.com/OWASP/ASVS/blob/v5.0.0/5.0/en/0x20-V11-Cryptography.md).

## GOV07 — 데이터 등급·보존·사용자 경계

데이터 대장은 공개·일반 내부·개인/민감·인증 비밀로 나누고 수집 목적·읽기/쓰기 주체·전송/저장 보호·로그·보존/삭제·백업·외부 전송을 적는다. 법적 보존기간은 여기서 추정하지 않고 실제 서비스·지역 확정 시 D10에서 검토한다.

인증 비밀은 URL/query·브라우저 영속 저장·분석 도구에 넣지 않는다. 로그와 메트릭도 제3자 전송이며 자동 수집을 포함해 검토한다. 필요하지 않은 개인정보를 수집하거나 내보내지 않는다. 사용 종료·만료·계정 전환 시 DOM·메모리·IndexedDB·서비스워커·클라이언트 캐시를 해당 범위만 정리한다.

별도 앱·신뢰하지 않는 업로드·PR preview는 다른 origin에서 제공한다. 동일 출처의 /preview 같은 경로 분리는 신뢰 격리가 아니다. 인증 쿠키를 공유하는 상위 Domain 속성 금지 기본안을 유지한다.

관련: ASVS V14 / 검증 V12, V29. 원문: [V14](https://github.com/OWASP/ASVS/blob/v5.0.0/5.0/en/0x23-V14-Data-Protection.md).

## GOV08 — 감사 무결성과 경보

애플리케이션·프록시·DB·인증·배포별 로그 대장에 이벤트·형식·수집기·보존기간·읽기/쓰기 주체·삭제 권한을 적는다. 앱의 기본 로깅은 Boot의 SLF4J/Logback을 사용하고 운영 출력은 기존 기능으로 UTC 구조화 JSON을 적용하는 방향이다. local은 비밀값을 정제한 가독성 있는 출력을 허용한다. 운영 배포 시 기존 중앙 수집기 연결·전송 TLS·읽기/쓰기 권한을 D06에서 확정하며, 수집기·APM·Prometheus/Grafana를 기본 프로젝트에 새 설치하지 않는다. 실행 계정은 중앙에 보관된 자기 과거 감사기록을 수정/삭제할 수 없어야 한다. 저장소 append-only/불변 보관 기능과 접근 감사로 검증한다.

기본 보존안은 감사 90일·진단 30일이며 서비스·법적 요구에 따라 D10에서 확정한다. 감사도 개인정보를 담을 수 있으므로 최소수집·권한·삭제 기준을 적용한다. 스택에 비밀값이 포함될 수 있어 접근 제한만으로 정제를 대체하지 않는다.

권한 거부·인증/복구/MFA·입력/업무 우회·TLS 실패·설정/배포 변경을 정해진 이벤트로 수집한다. 수집기/디스크 장애 시 buffer·손실량·알림을 제한하고, 중요한 상태 변경은 해당 감사의 실패 정책을 명세한다.

관련: ASVS V16 / 검증 V15, V30. 원문: [V16](https://github.com/OWASP/ASVS/blob/v5.0.0/5.0/en/0x25-V16-Security-Logging-and-Error-Handling.md).

## GOV09 — 패치·자원 관리

새로 확인된 finding의 긴급 차단/판정 목표: 실제 악용·Critical 24시간, High 72시간, Medium 14일, Low 30일 기본안. 기한은 알려진 시점부터 측정한다. 이는 릴리스 차단 기준을 완화하지 않으며 패치가 없으면 노출 차단·기능 비활성 또는 승인된 예외로 관리한다.

모든 연결·pool·thread·file·queue는 담당 계층, 동시 상한, timeout, 취소/close, 포화 응답, 재시도 횟수/총기한을 명세한다. 미정 제품의 pool 수치를 작업자가 무제한 기본값으로 대신하지 않는다. 외부 redirect 기본 금지, 필요 시 각 hop을 검증한다.

관련: ASVS V13.1, V15.1~15.2 / 검증 V14, V31.

## GOV10 — HTTP·배포 노출·서비스 인증

HTTP 80에서 브라우저 페이지의 안전한 GET/HEAD 탐색만 고정 HTTPS 주소로 전환한다. /api/**와 인증/변경 요청은 자동 redirect하지 않고 400 HTTPS_REQUIRED 또는 연결 차단 기본안. 이미 HTTP로 전송된 비밀은 redirect로 보호할 수 없다.

허용 메서드를 endpoint별 명시하고 TRACE/TRACK·미사용 메서드는 차단한다. 안전한 GET/HEAD/OPTIONS에 업무 변경을 넣지 않는다. 프록시와 서버의 중복/충돌 Content-Length·Transfer-Encoding 해석을 제품별 시험하고 불명확한 framing은 거부한다.

배포 산출물에 .git·소스/환경 파일·백업·덤프·테스트용 route·내부 API 문서를 넣지 않는다. Swagger/OpenAPI UI도 운영 기본 비공개. 내부 서비스 통신은 인증된 개별 신원·최소권한·가능하면 단기 자격을 사용한다. 고정 공유 비밀번호는 장기 기본안으로 취급하지 않고 지원 여부와 회전·완화책을 기록한다.

관련: ASVS V4·V12·V13 / 검증 V16, V32.

## GOV11 — 브라우저·헤더의 표준 차이

ASVS 3.4.1의 L2는 HSTS 하위 도메인 적용까지 포함한다. 기본 HDR05가 includeSubDomains를 자동으로 켜지 않는 것은 실제 하위 서비스 장애를 피하기 위한 단계적 도입 결정이다. 전체 하위 도메인 준비 전에는 L2 준수로 체크하지 않는다. preload는 L3 항목이며 기본 필수로 만들지 않는다.

기본 P1은 Next와 API가 한 앱의 동일 origin이다. 서로 독립된 앱·관리 시스템·비신뢰 콘텐츠를 그 origin에 합치지 않는다. postMessage 도입 시 정확한 발신 origin·schema·source 검증, JSONP 미지원, 외부 redirect allowlist, 지원되는 브라우저 보안 기능과 fallback 정책을 명세한다.

COOP·CSP 보고·SRI 등 L3 항목은 선택강화로 대조표에 남긴다. nonce 기반 enforcing CSP·frame-ancestors·no-store·nosniff·권한 검사는 기본 필수다. 보고 endpoint 도입 시 보고 내용의 URL/데이터 최소화·입력 크기·속도 제한을 설계한다.

관련: ASVS V3 / 검증 V08~V12, V33. 원문: [V3](https://github.com/OWASP/ASVS/blob/v5.0.0/5.0/en/0x12-V3-Web-Frontend-Security.md).

## GOV12 — JWT·OIDC 조건부 활성화 계약

P1 세션에서는 JWT/OAuth/OIDC를 구현하지 않는다. 채택 시 이 항목과 ASVS V9/V10 매핑을 활성화하고 관리형 IdP를 우선한다. 자체 authorization server를 “JWT 로그인”과 함께 자동 생성하지 않는다.

- token 서명/MAC·허용 알고리즘·신뢰된 키 출처·issuer·audience·exp/nbf·용도/typ 검증, access/ID/logout token 혼용 금지. 공격자가 지정한 jku/x5u/jwk·discovery URL을 임의 조회하지 않는다.
- 사용자 식별은 issuer+변경 불가 subject, 공급자 간 같은 이메일로 자동 연결 금지. 요청 scope와 부여 scope는 최소화한다.
- authorization code+PKCE S256, 요청별 state/nonce·code_verifier를 시작한 브라우저 세션/거래에 결합한다. redirect URI 정확 일치·mix-up 방어, code 단회·최대 10분(L3 선택 시 1분), implicit/password grant 금지.
- refresh token은 절대 만료·회전/재사용 탐지·관련 token 폐기. confidential client backchannel 인증, logout token은 전용 type/event·수신자·만료 검증, 강제 logout DoS 방지.
- acr/amr/auth_time가 요구 강도·최근 인증을 만족하는지 검사한다. 알 수 없으면 강한 인증으로 추정하지 않는다. 동의 화면·철회·최소scope·client 등록 정책은 IdP 책임과 앱 책임을 나눠 명세한다.
- DPoP/mTLS·PAR·동적 등록·authorization server·SSO 전파는 실제 사용 역할에만 적용한다. 미사용 증거와 선택 사유를 기록한다.

관련: ASVS V9·V10, V6.8·V7.6 / 검증 V34. 원문: [V9](https://github.com/OWASP/ASVS/blob/v5.0.0/5.0/en/0x18-V9-Self-contained-Tokens.md), [V10](https://github.com/OWASP/ASVS/blob/v5.0.0/5.0/en/0x19-V10-OAuth-and-OIDC.md).

## GOV13 — 추가 프로토콜·파일 조건

GraphQL 채택 시 depth/amount/cost 한도·권한·production introspection 정책, WebSocket은 WSS·handshake Origin·인증·메시지별 권한/크기/속도·만료/종료를 설계한다. 개발 HMR socket은 업무 WebSocket 기능이 아니다.

업로드·다운로드는 확장자/실제 형식·압축 해제 크기/개수·픽셀 상한·symlink/zip slip·파일명 header encoding·검사 전 격리·악성 판단/검사 실패·다운로드 권한을 포함한다. 서버가 생성한 파일도 사용자 입력 기반이면 같은 신뢰 경계를 적용한다.

WebRTC/TURN·미디어 서버는 미도입이다. 도입 시 특수 IP relay 차단·전용 DTLS/SRTP·키/신원·신호/미디어 자원 고갈 계약이 선행되어야 하며 기존 HTTP 테스트만으로 완료하지 않는다.

관련: ASVS V4.3/V4.4/V5/V17 / 검증 V21, V35.

## GOV14 — 공급망 본문 보완

배포 정책은 승인 source/ref·도구/환경·materials/SBOM·검사 결과·artifact digest를 연결하고 검증 주체를 지정한다. provenance/서명을 발급하는 주체는 가능한 한 untrusted build step과 격리하고, 배포 시 신뢰한 발급자·repository·workflow·ref·digest를 확인한다. 서명 파일의 존재만으로 통과시키지 않는다.

공개 PR의 임시 자원·권한은 완료/실패/취소 시 폐기한다. 감사에는 작성·검토·merge·build·deploy 주체와 revision을 남긴다. 두 사람 검토가 불가능한 개인 운영은 독립 리뷰 충족으로 표시하지 않고 예외를 남긴다. EDR 등 호스트 통제는 runner/OS 소유자 책임으로 구분한다.

근거: [NIST SP 800-204D §5.1~5.2](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-204D.pdf) 본문에서 증거 생성·검증 관점을 확인했다. 문서의 모든 권고를 의무 규정으로 채택한 것은 아니다. NSA/CISA 원문 직접 다운로드는 403이므로 본문 전체 확인을 주장하지 않으며 해당 자료를 유일 근거로 삼은 필수 항목은 없다. 검증 V19, V36.

## GOV15 — 선택 전 설계 동결과 변경

미선택 제품·기능은 미사용 조건부로 남기고, 실제 코드/설정으로 사용하지 않음을 검증한 뒤에만 N/A 확정한다. 공급망 도구·CIS·OS·DB별 세부 명령, 역할별 업무 API, 규제 보존기간은 해당 결정 관문에서만 확정한다.

동일 호스트 루프백의 평문 예외, 비밀번호 단독, HSTS 하위 도메인 미적용, 고정 서비스 자격 등은 각 표준 조항의 차이로 기록한다. “개인 템플릿이라 예외”라는 포괄 사유는 사용하지 않는다. 변경은 계약→구현→시험 순으로 관리한다.
