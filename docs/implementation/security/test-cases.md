# 공통 통제의 상세 검증 명세

> 2026-10-29 적용 갱신: 이 문서의 조항 ID는 유지한다. 인증/세션/동시3개/JWT조건부/로그 수준의 이전 기본안은 [최종 설계](../backend-foundation.md)로 대체하고 [새 검증표](../backend-handoff.md)의 BF/LG/AU/AT/FEI/SEC ID와 연결해 검증한다. 과거 검토·시험 기록을 새 JWT 기능의 통과 증거로 사용하지 않는다.

상태: 실행 계획. 생산 코드·제품별 명령은 D01~D07 이후 연결한다. 아래 신규 사례와 verification.md의 V01~V22를 모두 적용한다. 조건부 기능은 도입 시 활성화한다.

## 준비·관측 기준

합성 사용자 A/B, 다른 권한 사용자, 필요 시 테넌트 T1/T2를 준비하고 각자 소유 리소스와 숨겨야 할 필드를 구분한다. 시간은 서버의 주입 가능한 검사 clock 또는 실제 만료로 확인하며 운영 clock 우회 API는 만들지 않는다. 검사 전/후 상태와 외부 호출·감사 기록을 관측한다.

DB 변경·동시성·인증정보 변경 시험은 승인된 격리 범위만 실행한다. 실제 사용자·비밀값은 사용하지 않는다. 상태코드는 해당 API의 확정 계약이 우선하며 아래 기본값과 다르면 설계를 먼저 갱신한다.

| 시험 | 요구사항 | 입력·조건 | 통과 조건 |
| --- | --- | --- | --- |
| V23 | GOV01, API05 | scalar 중복 query, 중복 JSON key, query/body 충돌, 이중 encoding, prototype 경로, 최대 길이+1, 정수 overflow | 명세한 400/413, 권한 객체·실행 구조 불변, decode 후 검증 일관, 무한 처리 없음 |
| V24 | GOV02, API01 | A의 요청에 B/다른 테넌트 ID, 제한 필드 읽기·쓰기, role/owner 위조, 사용자 권한 철회 후 재요청 | 읽기는 제한 필드 미노출, 명세 밖 필드 400·금지 필드 403 기본안, 대리 서비스도 동일 권한, 다음 요청부터 철회 반영 |
| V25 | GOV03, API05 | 동일 자원에 병렬 변경, 단계 건너뛰기, 중복 key·같은 key 다른 payload, 전역 한도 경계 | 재고/고유성 보존, 성공 횟수 제한, 단계 위반 거부, 충돌 409 기본안, 중복 부수 효과 없음 |
| V26 | GOV04, API02 | 4개 동시 세션 생성, 목록/타 세션 종료, 비관리자의 관리자 종료 호출, 절대 만료 전후·비밀번호 변경 | 3개 정책 원자 적용, 오래된 세션 거부, raw token 미노출, 임의 사용자 종료 금지, 만료 서버 강제 |
| V27 | GOV05, API04 | MFA 미완료 세션, 등록되지 않은 대체 로그인, 현재 비밀번호 없는 변경, 만료/재사용/reset/MFA 복구 | 보호 기능 접근 거부, 동일 인증 강도, reset 단회 원자 소비·10분 기준, MFA 우회 불가, L2 차이 상태 정직 기록 |
| V28 | GOV06, API04, CFG04 | token 난수 생성 경로 검토, 같은 암호문 tag/nonce 변조, 잘못된 key ID·이전 key, hash 비용 변경 | CSPRNG 바이트 수 근거, 변조 plaintext 미반환, 키별 nonce 재사용 방지 근거, 회전/재암호화·password 재hash 계약 일치 |
| V29 | GOV07, FE01/FE03 | query·로그·APM·IndexedDB·serviceworker에 민감 canary, logout/계정 전환·다른 origin preview | 비승인 수집/공유 없음, 클라이언트 정리, 다른 앱/preview에 인증 쿠키·데이터 미전달 |
| V30 | GOV08, API07 | CR/LF 로그 입력, 여러 계층 동일 trace, 앱 계정으로 과거 감사 수정/삭제, 수집기 장애 | 구조화 로그 파괴 불가, 상관 분석 가능, 수정/삭제 거부, 제한 buffer·경보·실패 정책 일치 |
| V31 | GOV09, CI03/CI05 | 취약 의존성의 심각도/발견시각, 기한 경계, pool/queue 포화·timeout·취소 | 릴리스 정책과 일정 판정 일치, 기한 초과 미해결 차단, 자원 해제·한정된 실패, 무한 retry 없음 |
| V32 | GOV10, OPS02/OPS03 | HTTP /api 요청, 브라우저 페이지 HTTP GET, TRACE/미지원 메서드, .git/.env/backup/Swagger 경로 | API는 redirect 없이 거부, 페이지는 고정 HTTPS, 비공개 파일/관리 문서 접근 차단. framing 공격은 승인 격리 환경에서만 |
| V33 | GOV11, HDR05 | 하위 도메인 미준비·준비 상태, 별도 앱 동일 origin 검토, postMessage 유사 origin·잘못된 schema | 준비 전 HSTS L2 미충족 상태 표시, 준비 후 정책 검사, message 거부·origin 격리, 정상 흐름 유지 |
| V34 | GOV12, API06 | 잘못된 issuer/aud/alg/type/key URL·만료·replay·다른 세션 state·PKCE 오류·refresh 재사용·logout token | 각 단계 거부, 외부 임의 키 조회 불가, 전용 type/role 계약, 이미 발급한 관련 자격 폐기, scope 확대 없음 |
| V35 | GOV13, API06 | GraphQL 고비용, WebSocket 금지 origin/만료·홍수, 압축/픽셀/filename·symlink, WebRTC 특수 주소 | 도입 기능의 제한/권한/거부 계약 충족. 미도입은 경로·의존성·포트 없음 증거 |
| V36 | GOV14, CI02~CI05 | 다른 repo/workflow/ref/digest의 서명 증거, 검사 없는 artifact, PR 취소·실패 | 검증기 거부, 신뢰한 빌드만 배포, 임시 자원/권한 폐기, revision과 감사 연결 |

## 기존 시험 보강

- V03은 최소 두 소유자와 필드별 읽기/쓰기까지 포함한다. V04는 절대·유휴 수명과 세션 목록/폐기를 함께 관측한다.
- V06은 CSRF 없는 변경뿐 아니라 로그인 직후 첫 변경과 로그아웃 후 재발급을 별도 실행한다. 실패 응답만 보는 것이 아니라 업무 상태가 바뀌지 않았음을 확인한다.
- V08은 HTML/API/정적 자원/프록시 오류별로 헤더를 판단한다. 전체 경로에 같은 CSP를 억지 적용하지 않는다.
- V14의 지연은 비동기·한정 큐로 처리되어야 하며 타인 계정 영구 잠금이나 thread 점유를 만들지 않아야 한다.
- V18은 복구된 데이터·만료/폐기 자격·백업키 보존을 각각 확인한다. 백업 파일 생성만으로 복구 통과가 아니다.
- V20은 인증된 경로와 역할별 탐색 범위를 기록한다. 스캐너가 방문하지 않은 경로는 미검증이다.

## 확인한 WSTG 본문과 연결

| 공식 시험 | 이 프로젝트 연결 |
| --- | --- |
| [v4.2 WSTG-SESS-05](https://wstg.owasp.org/v4.2/4-Web_Application_Security_Testing/06-Session_Management_Testing/05-Testing_for_Cross_Site_Request_Forgery/) | V06: 출처가 다른 문맥의 요청과 토큰 조건을 나눠 시험 |
| [v4.2 WSTG-ATHZ-04](https://wstg.owasp.org/v4.2/4-Web_Application_Security_Testing/05-Authorization_Testing/04-Testing_for_Insecure_Direct_Object_References/) | V03/V24: 소유자가 다른 ID를 실제로 교체하고 읽기/변경 거부 관측 |
| [v4.2 WSTG-BUSL-04](https://wstg.owasp.org/v4.2/4-Web_Application_Security_Testing/10-Business_Logic_Testing/04-Test_for_Process_Timing/) | V25/V31: 시간 의존 업무의 경계·지연·반복 요청을 계약과 비교 |

WSTG 전체 항목의 적합성 매핑이나 검사 통과 기록은 아니다. 프로젝트 공통 검증은 ASVS 대조와 위 시나리오로 구성하며 제품별 명령은 선택 후 추가한다.

## 증거 최소 양식

시험 ID / 요구사항·ASVS ID / 환경·revision·artifact / 합성 fixture / 실행 도구·버전·명령 / 입력·예상 / 실제 응답·상태 불변성 / 정제된 증거 위치 / 통과·실패·미실행·N/A 이유.

조건부 항목의 N/A는 구현 검사로 미사용을 확인해야 한다. 현재 모든 동작 시험은 미실행이다.
