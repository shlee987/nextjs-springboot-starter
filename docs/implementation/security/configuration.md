# 환경·설정·비밀값 계약

CFG ID는 구현 시 설정 정의·시작 검증·검사 증거에 연결한다. 이 표의 변수명은 프로젝트 계약 기본안이며 Spring/Next 내장 환경변수라고 가정하지 않는다. 확정 버전표에 맞춰 구현 시 바인딩 코드·설정 파일·빌드 옵션으로 매핑하고 동일 값을 여러 곳에 중복 정의하지 않는다.

## CFG01 — 환경 입력 표

| 입력·정책 | 소비자·시점 | 필수·기본값 | 검증 |
| --- | --- | --- | --- |
| 배포 환경 | backend 시작 / frontend 빌드·개발 | local/dev/stg/prod 중 하나 명시 | backend는 SPRING_PROFILES_ACTIVE, frontend는 APP_ENV 빌드 입력. frontend production 시작은 환경 재주입 없이 빌드 결과 사용. NODE_ENV와 구분하며 상세는 initial-setup.md |
| APP_PUBLIC_ORIGIN | backend 실행 / frontend 빌드 입력 | dev/stg/prod 필수, local은 http://localhost:3000 기본안 | 단일 절대 origin, 운영 HTTPS, userinfo·path·query·fragment 금지 |
| BACKEND_INTERNAL_ORIGIN | Next 서버, 실행 시 | 서버→API 호출을 도입할 때 필수 | 승인 내부 목적지, 사용자 입력 불가, 브라우저 공개 금지 |
| APP_ALLOWED_ORIGINS | Spring CORS, 실행 시 | P1은 빈 목록 | 승인 D03 분리 출처만. scheme·host·port 정확 비교, *·null 금지 |
| 신뢰 프록시 목록 | 진입점·서버 | 직접 연결이면 비활성, 프록시 사용 시 필수 | 실제 proxy CIDR/보안그룹으로 제한, 임의 X-Forwarded-* 신뢰 금지 |
| DB 연결·사용자·비밀 | backend, 실행 시 | DB 기능 채택 시 필수 | 운영 빈 값·예제 값·관리계정 거부. 로그 출력 금지 |
| DB TLS·CA | DB client, 실행 시 | 운영 TLS+호스트·인증서 검증 필수 | trust-all·검증 생략·가짜 CA fallback 금지 |
| 세션 저장소 연결 | API, 실행 시 | Redis 공유 세션 기반에서 필수, 단일 서버도 동일 경로 | endpoint·ACL·TLS·namespace·TTL·serializer·장애 동작 검사. local 예외는 지정 범위만 |
| 세션 유휴/절대 수명 | API | 30분/8시간 기본안 | 양수, 절대 >= 유휴, 서버 강제 |
| 인증·일반 요청 한도 | API/진입점 | backend.md 수치 기본안 | 0·음수·무제한 금지, 저장소 공유 및 장애 정책 |
| API 크기·타임아웃·페이지 | 각 계층 | backend.md 계약 | 단위와 적용 지점 일치, parser 이전 제한 |
| 로그 수준·출력 | 전체 | 운영 INFO, 필요한 ERROR 진단 | DEBUG/TRACE·HTTP body 수집 기본 차단 |
| 쿠키 보안 속성 | API | 운영 고정, backend.md | 운영에서 Secure/HttpOnly 끄는 옵션 금지 |
| CSP 외부 출처 | Next | 기본 빈 허용목록 | 지시어·정확한 출처별 승인 기록. 사용자 문자열 직접 결합 금지 |
| 시크릿 공급 경로 | 운영 | 배포 제품 선택 후 필수 | 최소권한 읽기, 마운트 권한, 회전·폐기 절차 |
| OIDC·메일·저장소 키 | 해당 모듈 | 미도입 시 설정·기능 없음 | 기능 활성 시에만 필수 검증, 가짜 기본 자격 금지 |

APP_PUBLIC_ORIGIN은 공개 가능한 주소지만 환경별 설정의 원본은 배포 설정이다. backend는 실행 시, frontend는 현재 빌드 시 검증하며 frontend 공통 start에 재주입하지 않는다. NEXT_PUBLIC_ 주입으로 실행 시 변경되는 설정처럼 취급하지 않는다. frontend는 CSP 환경 구분에 필요한 비밀 없는 APP_ENV만 next.config.env의 명시 허용값으로 정적 반영할 수 있다. 내부 API 주소·origin 전체·비밀·전체 process.env를 함께 복사하지 않는다. 다음 nonce는 설정값으로 두지 않으며 요청마다 생성한다.

배포 프로파일·Next 환경 선택·파일 구분은 [1차 환경 계약](../initial-setup.md)을 따른다. 문서의 staging/production은 환경 의미이며 실제 이름은 stg/prod다. backend는 공통·환경별 application YAML을 사용하고 실제 비밀값은 외부에서 주입한다. Next는 APP_ENV를 빌드·개발 입력으로 사용하고 환경별 빌드 명령이 config/env의 선택 파일을 공급한다. production 실행은 npm start 하나이며 별도 환경 선택·설정 재주입·자체 마커를 요구하지 않는다. Next 기본 환경변수 처리와 명시한 빌드값 검증을 적용한다. 공개 허용된 비밀 없는 값만 빌드에 반영하며 내부 주소·자격을 일반 env 복사로 공개하지 않는다. 명령·파일·우선순위·소비 시점 기준은 initial-setup.md가 원본이다.

## CFG02 — 파일과 우선순위

- 공개 관리: 예제 환경변수 파일, 비밀 없는 공통 설정, 도구·빌드 버전, lockfile, 정책 문서.
- 로컬 비공개: .env·개인 override·실제 키/인증서·IDE 개인 설정·로그·DB 덤프·빌드 및 검사 중간 산출물.
- .gitignore는 실제 모듈에 맞춰 작성하고 .env.example 등 승인된 예제만 예외로 추적한다. 인증서 확장자를 모두 무조건 제외하면 공개 CA fixture까지 숨길 수 있으므로 공개 fixture와 개인키를 구분한다.
- .dockerignore와 CI artifact allowlist도 별도로 관리한다. .gitignore만으로 Docker context·캐시·배포 패키지 유출을 막을 수 없다.
- Spring은 공통 비밀 없는 기본값 → 환경별 설정 → 외부 주입의 최종 유효값을 검증한다. 실제 framework의 상세 우선순위를 문서화하고 중복 override를 줄인다.
- .env가 Spring에서 자동 로드된다고 가정하지 않는다. 선택한 실행 방식에서 환경변수 또는 안전한 파일 주입을 명세한다.
- .env.example의 비밀값은 빈 값 또는 명백한 placeholder로만 두고 운영 시작 검증에서 거부한다. README 예제에 실제 조직명·개인 이메일·접속주소·원격 자격을 적지 않는다.
- IDE 설정과 개발자 PC 경로를 실행 전제로 만들지 않는다. 공개 문서의 예제 경로는 저장소 상대 경로만 사용한다.

## CFG03 — 시작 검증

안전한 운영 시작 실패 항목: 필수 자격 누락/빈 값/placeholder, 불명확한 환경, 잘못된 origin, 운영 HTTP 공개 origin, 부적합한 쿠키, CORS wildcard, 필수 세션 저장소 누락, 금지된 개발 플래그.

권한·DB TLS처럼 연결해야 알 수 있는 조건은 readiness 단계에서 검사하고 트래픽을 받지 않는다. liveness는 자격 원문·DB 상세를 외부로 노출하지 않는다. 실패 메시지는 변수명·안전한 이유만 표시하고 설정 전체를 dump하지 않는다.

## CFG04 — 비밀값 수명주기

생성은 CSPRNG 또는 공급자 발급, 서비스·환경별 분리, 최소권한 읽기·단기 자격 우선, 만료·회전·폐기·감사를 설정한다. 저장 암호화 키를 암호문과 함께 저장하지 않는다. 백업·runner·APM·오류 수집에도 같은 보호를 적용한다.

유출 의심 시 기록 삭제만으로 완료하지 않는다. 자격 폐기·재발급 → 영향 조사 → 소스/로그/캐시/산출물 정리 → 재검사를 수행한다. Git 이력 정리는 노출된 키의 유효성을 취소하지 못한다.

## CFG05 — 검증

각 필수값 누락·빈 값·placeholder·형식 오류를 독립 검사하고 서비스가 트래픽을 받지 않는지 확인한다. 빌드 때 가상의 canary 값을 주입해 브라우저 번들·HTML·RSC·로그·컨테이너 layer·산출물에 포함되지 않는지 검사한다. 실제 키로 유출 검사를 수행하지 않는다.

근거: [OWASP Secrets Management](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html), [공통 값 배치 규칙](../../common-rules/docs/conventions/common/values.md). 세부 변수명·정책은 이 프로젝트의 설계안이다.

## DB·Redis 접속의 환경 독립성

[로컬 환경 이식 계약](../local-environment.md)과 [공유 세션](../authentication-foundation.md)을 적용한다. Docker service 이름·호스트 port·로컬 파일 경로를 제품 코드에 고정하지 않는다. local 예제의 DB/Redis 연결·공개 테스트 자격은 application-local.yml에 기본값을 제공한다. dev/stg/prod의 DB/Redis 연결·자격·TLS·CA·topology는 외부 설정으로 공급하며 local 기본값을 상속하지 않는다. session namespace는 앱/환경별로 고정하고 노드별로 바꾸지 않는다. Redis 운영 설정 누락·접속 실패 시 Docker local 설정이나 메모리로 fallback하지 않는다.

## CFG06 — Boot 4.1 오류 정보 기본 비공개 설정

공통 application.yml에 아래 정책을 명시하고 local·dev·stg·prod의 최종 유효값 모두에서 유지한다. 프로파일·환경변수·명령행 override로 완화되면 설정 검증에서 거부한다.

| 설정 키 | 값 |
| --- | --- |
| spring.web.error.include-exception | false |
| spring.web.error.include-message | never |
| spring.web.error.include-binding-errors | never |
| spring.web.error.include-stacktrace | never |
| spring.web.error.include-path | never |
| spring.web.error.whitelabel.enabled | false |

위 키는 채택한 Boot 4.1.2 공식 속성표에서 확인했다. 과거 server.error.* 키만 복사하고 동일하게 동작한다고 가정하지 않는다. include 옵션을 always/on_param으로 두거나 요청 파라미터로 원문 출력을 활성화하지 않는다.

이 설정은 Boot 기본 fallback의 방어선이다. ControllerAdvice·Security 필터·앱 필터·/error와 직접 반환하는 ProblemDetail은 [공통 응답의 예외 비노출 계약](../api-contract.md)에 따라 별도로 정제한다. Whitelabel 비활성화만으로 컨테이너·프록시 오류 페이지까지 안전해진다고 판단하지 않는다.

근거: [Boot 공통 속성표](https://docs.spring.io/spring-boot/appendix/application-properties/index.html), [Spring 오류 응답과 확장 필드](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html). 기본값에 기대지 않고 명시하는 정책과 모든 프로파일 적용은 프로젝트 선택이다.
