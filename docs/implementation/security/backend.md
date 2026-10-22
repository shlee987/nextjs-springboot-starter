# API·인증·권한·데이터 설계

D04를 해소하기 전 인증 코드를 생성하지 않는다. 아래 P1 세션 방식은 설계 기본안이다. 제품 API 경로·역할 표는 [공통 명세 양식](../engineering.md)으로 확정한다.

## API01 — 접근 제어와 필터

| 항목 | 필수 계약 |
| --- | --- |
| 공개 경로 | 명시적 목록만. 경로 패턴과 HTTP 메서드를 함께 지정 |
| 보호 경로 | 인증 후 역할·소유권·테넌트·업무 상태를 서버에서 확인 |
| 미정의 경로 | 보안 정책은 deny-by-default. MVC의 정상 404와 구분 |
| 자원 조회 | 요청 userId·role·tenantId를 신뢰하지 않고 인증된 주체에서 도출. 소유권을 조회·변경 조건에 포함 |
| 실패 | 인증 필요 401, 인증됐지만 금지 403, 보호 자원 존재 은닉은 해당 API에서 일관된 404 정책 |
| 관리·Actuator | 업무 공개 경로와 분리. 내부 인증과 네트워크 제어 둘 다 필요 |
| 예외 경계 | Security 필터의 EntryPoint·AccessDeniedHandler와 ControllerAdvice가 같은 오류 계약 사용 |
| 필터 범위 | 에러 dispatch·비동기·프록시 경로 우회 포함. API permitAll·필터 제외 일괄 지정 금지 |

권한은 endpoint·서비스의 실제 업무 경계에서 강제하고 ID를 UUID로 바꾸는 것으로 대체하지 않는다.
근거: [OWASP Authorization](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html).

## API02 — P1 인증·세션 기본안

| 정책 | 프로젝트 기본안 |
| --- | --- |
| 세션 발급 | Spring이 검증한 사용자에 한해 프레임워크 세션 생성. 자체 세션 토큰 알고리즘 금지 |
| 운영 쿠키 | __Host-session, Secure, HttpOnly, SameSite=Lax, Path=/, Domain 미지정, 브라우저 세션 쿠키 |
| local HTTP 쿠키 | 별도 이름 dev-session, HttpOnly, Lax, Path=/, Domain 미지정, Secure 예외는 루프백만 |
| 수명 | 유휴 30분, 절대 8시간. 서버 강제, 유휴 만료 설정만으로 절대 만료를 구현했다고 판단 금지 |
| 세션 교체 | 로그인·권한 상승 시 ID 교체, 이전 ID 무효화. 비밀번호 변경·계정 정지 때 관련 세션 폐기 |
| 로그아웃 | CSRF 보호된 변경 메서드, 서버 세션 폐기와 동일 속성으로 쿠키 삭제. GET 로그아웃 금지 |
| 세션 전달 | 쿠키만 허용, URL rewriting·쿼리 세션 ID 금지 |
| 수평 확장 | 공유 저장소·TTL·암호화·접근권한을 D05에서 확정. 재시작/저장소 장애는 인증 우회 없이 실패 |
| 재인증 | 비밀번호·이메일·권한·결제 등 민감 변경은 최근 인증 또는 MFA 조건을 API별로 명시 |

출처: [OWASP Session Management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html). 쿠키명·시간·프로필은 프로젝트의 선택이다. 쿠키가 Next에도 전달되는 단일 출처 기본안이므로 로그·서버 호출 전달 범위를 통제한다.

## API03 — CSRF와 CORS

- 쿠키 인증에서는 CSRF 유지. SameSite·CORS·API라는 이름을 근거로 해제하지 않는다.
- 기본안은 Spring의 세션 CSRF 저장소와 JSON 토큰 발급 endpoint다. API 명세에서 고정한 endpoint는 토큰을 실제 materialize하고 token·headerName만 반환하며 Cache-Control: no-store를 적용한다.
- 브라우저 공통 요청 경계는 발급된 토큰을 지정 헤더로 POST·PUT·PATCH·DELETE에 전달한다. 누락·오류는 403과 고정 오류코드로 반환한다.
- 발급 endpoint는 미인증에서도 로그인 CSRF 토큰을 받을 수 있으나 공개된 비밀 endpoint로 오해하지 않는다. 다른 출처에서 읽을 수 없도록 하며 발급 남용에도 한도를 적용한다.
- Spring의 deferred token·BREACH 표현과 resolve 동작을 선택한 버전에서 일치시킨다. 쿠키의 raw token과 XOR token을 혼용하지 않는다.
- 로그인·로그아웃으로 기존 CSRF 토큰이 초기화되면 다시 발급받는다. 인증 성공부터 첫 변경 요청까지 검증한다.
- P1은 같은 출처이므로 브라우저용 cross-origin 허용 출처 목록이 비어 있는 기본안이다. CORS 헤더가 없다는 사실은 비브라우저 클라이언트 차단이 아니다.
- 분리 출처가 필요하면 정확한 scheme·host·port만 허용, credentials=true와 * 조합 금지. 허용 메서드/헤더·preflight 캐시를 명세하고 CORS를 보안 인증 필터보다 먼저 처리한다.
- 변경 요청의 Origin/Referer 검사를 추가할 경우 누락·null·비브라우저 요청 처리까지 API 계약으로 확정한다. 이를 CSRF의 대체로 사용하지 않는다.

근거: [Spring CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [Spring CORS](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html). spa() 등 버전별 편의 API를 버전 확인 없이 복사하지 않는다.

## API04 — 비밀번호·인증 남용

자체 비밀번호 인증을 채택한 경우 아래가 필수다. 외부 IdP 선택 시 IdP에 위임되는 항목과 애플리케이션 책임을 다시 적는다.

- 기본 정책은 MFA 없는 경우 15~128 Unicode code point, MFA 강제 시 하한 8을 허용하는 별도 결정. trim·자동 절단·강제 대소문자/특수문자 조합·근거 없는 주기 변경 금지.
- 흔한·유출 비밀번호 목록 검사. 외부 조회 도입 시 원문 비밀번호를 외부로 전송하지 않는 프로토콜·장애 정책 확정.
- 프로젝트 기본안 Argon2id m=19456 KiB, t=2, p=1 이상, 랜덤 salt 라이브러리 관리. 배포 하드웨어에서 부하·지연·동시성 시험 뒤 비용 확정, 알고리즘 식별자 포함 저장.
- Spring encoder 라이브러리·의존성·파라미터 API를 버전 고정 후 명세. 평문·가역 암호·단순 SHA 해시 금지.
- 존재/미존재/잠김 계정의 실패 메시지·상태 일관성 유지, 미존재 계정도 유사 비용 경로. 완전 동일 응답시간을 보장한다고 표현하지 않는다.
- 로그인은 계정 식별자와 신뢰된 원본 IP를 함께 제한. 기본안 계정별 15분 내 5회 실패 이후 1·2·4·8·16·30초 단계 지연(최대 30초), IP별 30회/분. 지연은 비동기·한정 큐로 처리하고 thread 점유·계정 영구 잠금 금지. 분산 공격·타인 계정 방해를 별도 시험한다. 성공만으로 IP 한도를 무조건 초기화하지 않음.
- 한도 초과는 429·Retry-After. 영구 계정 잠금으로 타인 계정 DoS를 만들지 않음. 한도 저장소를 여러 인스턴스가 공유하고 장애 시 인증 경로는 안전한 503으로 실패.
- 비밀번호 재설정·회원가입·MFA·메일 변경을 도입하면 단회 토큰·만료·재인증·세션 폐기·재사용 차단 계약을 추가하기 전 공개하지 않음.
- 관리자 인증에 MFA 필요. IdP·MFA가 미구현이면 관리자 기능 운영 공개를 보류한다.

근거: [OWASP Authentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html), [Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html). 길이 상한·한도·장애 응답 수치는 프로젝트 기본안이다.

## API05 — 입력·오류·리소스 제한

| 항목 | 계약 |
| --- | --- |
| DTO | 허용 필드만 바인딩, entity 직접 바인딩 금지. 알 수 없는 필드는 기본 400. 필드 타입·길이·범위·null·배열/중첩 상한 명세 |
| 파싱 전 제한 | 일반 API body 1 MiB, 헤더 16 KiB, JSON 최대 깊이 32 기본안. 프록시와 서버가 실제 경계에서 강제 |
| Content-Type | JSON API는 명시한 JSON 타입만, 잘못된 타입 415·형식 오류 400·크기 초과 413 |
| 조회 | 기본 page size 20, 최대 100. 무제한 전체 조회 금지, 정렬 필드 허용목록·안정적인 tie-breaker |
| SQL | 값 바인딩, 정렬·식별자는 고정 허용 분기. 문자열 결합 SQL 금지 |
| 변경 | 소유권·업무 조건과 함께 트랜잭션, 기대 행 수 확인. 필수 ID·빈 목록이 전체 수정/삭제로 바뀌지 않음 |
| 역직렬화 | JSON default typing·사용자 타입명·Java native serialization 금지. XML 기능 미도입 |
| 외부 요청 | 연결 3초·응답 10초·전체 15초 기본안, 크기 제한과 취소. 변경 요청 자동 재시도 기본 금지 |
| 일반 rate limit | 인증 주체 120회/분, 미인증 IP 60회/분 기본안. 역할·경로별 조정은 사유 기록, 무한 큐 금지 |

프록시와 애플리케이션 제한의 경계값과 둘 중 어디서 거절됐는지를 검증한다. 요청 압축은 기본 미지원, 도입 시 압축 해제 후 크기도 제한한다. timeout은 실제 라이브러리 의미가 다르므로 단위·범위를 구성별 매핑한다.

오류 기본안은 Problem Details 계열 공통 JSON: type(승인된 고정 문서 URI), title(안전한 문구), status(HTTP와 일치), code(고정 식별자), traceId(서버 생성). 검증 오류에는 허용된 field·code만 추가하며 rejectedValue·스택·SQL·경로·원문 예외를 반환하지 않는다. 204 응답에는 본문이 없다.

근거: [REST Security](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html), [Input Validation](https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html). limit 값과 오류 스키마는 프로젝트 설계안이며 별도 API 문서에서 최종 고정한다.

## API06 — 추가 기능의 안전 조건

| 기능 | 도입 시 필수 설계 |
| --- | --- |
| 파일 업로드 | 크기·개수·확장자·실제 포맷 허용목록, 서버 생성 저장명, 웹루트 밖 비공개 저장, 실행 불가, 악성코드 검사/격리·시간초과 정책, 다운로드 권한·attachment, 경로 탈출·압축 폭탄 시험 |
| URL 조회/SSRF | 목적지 allowlist, scheme·port 제한, 해석된 IPv4/IPv6·loopback/private/link-local/metadata 차단, redirect 기본 비활성, DNS 재바인딩과 실제 연결 검증, egress 방화벽 |
| 웹훅 | 서명·timestamp·replay 차단·body 크기·idempotency·키 회전, 일반 사용자 세션과 다른 인증 계약 |
| 배치 | API와 같은 자격·권한·감사 보호, 실행 주체·중복/재실행·부분 실패·복구 계약 |
| JWT/OIDC | issuer·audience·서명 알고리즘·만료·키 교체·nonce/state/PKCE 해당 흐름·폐기·저장 위치를 별도 설계, 자체 JWT 발급을 자동 도입하지 않음 |

현재 미도입 기능은 route·의존성·설정 자체를 추가하지 않고 N/A 사유로 남긴다.
근거: [File Upload](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html), [SSRF](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html).

## API07 — 로그·감사

서버 생성 traceId, UTC 시각, 이벤트명, 내부 주체 ID, 대상 유형/ID, 결과, 고정 원인 코드를 기록한다. 로그인 성공/실패, 권한 거부, 세션 폐기, 관리자 변경, 설정 변경, 한도 초과·주요 장애를 분리한다.

비밀번호·토큰·쿠키·Authorization·CSRF·전체 요청/응답·실제 개인정보는 기록하지 않는다. 로그 입력의 CR/LF를 구조화 인코딩하고 길이를 제한한다. URL query·예외 원문·외부 오류·APM의 자동 수집도 점검한다. 스택은 접근 제한 진단 채널에 정제해 기록하고 같은 오류를 계층마다 반복 기록하지 않는다.

감사 전송 장애 시 중요 업무의 fail-open/fail-closed 정책은 API별로 확정한다. 무한 대기·무한 버퍼·디스크 고갈을 허용하지 않는다.
근거: [OWASP Logging](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html).

## 본문 대조 보완 0.2

- 필드별 권한·대리 주체: [GOV02](controls.md), V24.
- 업무 상태·동시성·idempotency: GOV03, V25.
- 동시 세션 3개·사용자/관리자 폐기: GOV04, V26.
- 자체 비밀번호 변경·MFA·복구와 L2 차이: GOV05, V27. 관리자 MFA만으로 전체 L2 충족을 표시하지 않는다.
- 입력 해석·prototype·중복 key·인젝션: GOV01, V23.
- 감사 무결성·중앙 수집: GOV08, V30.
- JWT/OIDC·추가 프로토콜: GOV12/GOV13, 도입 시 V34/V35.

위 항목은 기존 원칙에 추가하는 상세 계약이다. 관련 D 관문과 확정 API 명세를 우선 확인한다.
