# 환경·설정·비밀값 계약

CFG ID는 구현 시 설정 정의·시작 검증·검사 증거에 연결한다. 이 표의 변수명은 프로젝트 계약 기본안이며 Spring/Next 내장 환경변수라고 가정하지 않는다. 실제 버전 확정 후 바인딩 코드·설정 파일·빌드 옵션으로 매핑하고 동일 값을 여러 곳에 중복 정의하지 않는다.

## CFG01 — 환경 입력 표

| 입력·정책 | 소비자·시점 | 필수·기본값 | 검증 |
| --- | --- | --- | --- |
| 배포 환경 | 모듈 시작·배포 manifest | local/test/staging/production 중 명시 | 운영 manifest에 local 설정 혼입 금지, NODE_ENV와 별개 |
| APP_PUBLIC_ORIGIN | API·진입점, 실행 시 | staging/production 필수, local은 http://localhost:3000 기본안 | 단일 절대 origin, 운영 HTTPS, userinfo·path·query·fragment 금지 |
| BACKEND_INTERNAL_ORIGIN | Next 서버, 실행 시 | 서버→API 호출을 도입할 때 필수 | 승인 내부 목적지, 사용자 입력 불가, 브라우저 공개 금지 |
| APP_ALLOWED_ORIGINS | Spring CORS, 실행 시 | P1은 빈 목록 | 승인 D03 분리 출처만. scheme·host·port 정확 비교, *·null 금지 |
| 신뢰 프록시 목록 | 진입점·서버 | 직접 연결이면 비활성, 프록시 사용 시 필수 | 실제 proxy CIDR/보안그룹으로 제한, 임의 X-Forwarded-* 신뢰 금지 |
| DB 연결·사용자·비밀 | backend, 실행 시 | DB 기능 채택 시 필수 | 운영 빈 값·예제 값·관리계정 거부. 로그 출력 금지 |
| DB TLS·CA | DB client, 실행 시 | 운영 TLS+호스트·인증서 검증 필수 | trust-all·검증 생략·가짜 CA fallback 금지 |
| 세션 저장소 연결 | API, 실행 시 | 다중 인스턴스 P1이면 필수 | private endpoint·인증·TLS·TTL·장애 동작 검사 |
| 세션 유휴/절대 수명 | API | 30분/8시간 기본안 | 양수, 절대 >= 유휴, 서버 강제 |
| 인증·일반 요청 한도 | API/진입점 | backend.md 수치 기본안 | 0·음수·무제한 금지, 저장소 공유 및 장애 정책 |
| API 크기·타임아웃·페이지 | 각 계층 | backend.md 계약 | 단위와 적용 지점 일치, parser 이전 제한 |
| 로그 수준·출력 | 전체 | 운영 INFO, 필요한 ERROR 진단 | DEBUG/TRACE·HTTP body 수집 기본 차단 |
| 쿠키 보안 속성 | API | 운영 고정, backend.md | 운영에서 Secure/HttpOnly 끄는 옵션 금지 |
| CSP 외부 출처 | Next | 기본 빈 허용목록 | 지시어·정확한 출처별 승인 기록. 사용자 문자열 직접 결합 금지 |
| 시크릿 공급 경로 | 운영 | 배포 제품 선택 후 필수 | 최소권한 읽기, 마운트 권한, 회전·폐기 절차 |
| OIDC·메일·저장소 키 | 해당 모듈 | 미도입 시 설정·기능 없음 | 기능 활성 시에만 필수 검증, 가짜 기본 자격 금지 |

APP_PUBLIC_ORIGIN은 공개 가능한 주소지만 운영 설정의 원본은 배포 설정이다. NEXT_PUBLIC_ 주입으로 서버 실행 시 설정처럼 바꾸려 하지 않는다. 다음 nonce는 설정값으로 두지 않으며 요청마다 생성한다.

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
