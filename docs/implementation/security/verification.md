# 보안 검증과 완료 판정

> 2026-10-29 적용 갱신: 이 문서의 조항 ID는 유지한다. 인증/세션/동시3개/JWT조건부/로그 수준의 이전 기본안은 [최종 설계](../backend-foundation.md)로 대체하고 [새 검증표](../backend-handoff.md)의 BF/LG/AU/AT/FEI/SEC ID와 연결해 검증한다. 과거 검토·시험 기록을 새 JWT 기능의 통과 증거로 사용하지 않는다.

상태: 검증 계획. 애플리케이션·서버·CI가 아직 없으므로 아래 시험은 실행하지 않았다. 문서 경로 점검 통과와 시스템 보안 통과를 구분한다.

## 증거 관리

각 요구사항에 ID, 적용/조건부/N/A, 구현 경로·설정 키, 환경·commit/artifact digest, 도구/룰 버전·실행 시각, 입력·기대 결과·실제 결과, 안전하게 정제한 증거 위치, finding/예외 ID를 연결한다.

검사 로그와 임시 파일은 외부 작업 공간 또는 접근 제한 CI 저장소에 둔다. 저장소에는 비밀·개인정보 없는 결과 요약만 둔다. 실제 운영 주소·계정·토큰·세션·요청 전문을 붙이지 않는다. 각 시험의 해당 요구사항 전부에 결과를 연결하고 대표 ID 하나만 체크하지 않는다.

## 필수 시험 표

| 시험 | 요구사항 | 실행·입력 | 기대 결과 |
| --- | --- | --- | --- |
| V01 | SEC01, ENG02, CI03 | 깨끗한 checkout에서 고정 도구·의존성으로 검사/생산 빌드 | 문서화 명령 재현, 선택 버전/지원기간 확인 |
| V02 | CFG01~CFG05, SEC04 | 필수값 각각 누락·빈 값·placeholder·잘못된 origin·운영 dev 플래그 | 시작 실패 또는 ready=false, 원문 자격 미출력 |
| V03 | API01, SEC02 | 익명·일반·다른 사용자·다른 테넌트·정지 계정·관리자 권한 표 | 명세한 401/403/404. UUID·숨김 메뉴로 우회 불가 |
| V04 | API02, API04 | 로그인 전후·권한 상승·유휴/절대 만료·로그아웃·계정 정지 | ID 교체, 이전/만료 세션 거부, 민감 변경 재인증 |
| V05 | API02, HDR02 | 실제 HTTPS Set-Cookie와 local 예외 비교 | Secure·HttpOnly·Lax·Path·Domain·__Host prefix 조건 일치 |
| V06 | API03 | CSRF 누락·오류·로그인 전/후·로그아웃 후·정상 토큰 | 변경 요청 403 또는 정상 계약. 첫 요청·토큰 재발급 정상 |
| V07 | API03 | 허용/금지/유사 origin·null·다른 포트·preflight | 금지 출처 readable credential 응답 없음. 허용 origin만 정확 반영 |
| V08 | HDR01~HDR06 | 생산 빌드·진입 프록시에서 일반/보호/로그인/404/500/413/429/502 GET | 해당 헤더 값·중복·캐시·안전한 본문 일치 |
| V09 | HDR03, HDR06, HDR07 | 두 요청 nonce 비교·위조 x-nonce·새로고침·prefetch·RSC·라우트 이동 | nonce 신규·문서 내 일치, 정상 UI에 예상 밖 CSP 위반 없음 |
| V10 | FE02, HDR03, HDR07 | 격리된 XSS/inline event/nonce 없는 script/외부 frame 검증 | 실행·임베딩 차단, 검증 코드 운영 산출물 미포함 |
| V11 | FE01, FE04, CFG05 | 합성 canary 비밀을 서버에 주입하고 HTML/JS/RSC/map/log/image/artifact 검색 | 브라우저·공개 결과물에 서버 canary 미노출 |
| V12 | FE03, HDR04 | 사용자 A/B·로그아웃·뒤로가기·CDN·서버 fetch 캐시 | 교차 사용자 데이터·nonce 재사용 없음, 보호 응답 no-store |
| V13 | API05, SEC03 | 경계 길이/크기/JSON 깊이/배열·unknown field/정렬·SQL 문자·잘못된 type | 400/413/415 등 계약과 일치, 쿼리/실행 문맥으로 탈출 불가 |
| V14 | API04, API05, OPS02 | 계정/IP 한도·위조 forwarded IP·다중 인스턴스·저장소 장애 | 429·Retry-After 또는 계약한 503, 우회·무한 대기 없음 |
| V15 | API05, API07, CFG06, OPS03 | 필터·MVC·/error·프록시 오류, 중첩 cause 합성 표식·노출 유도 파라미터·로그 injection | 허용 오류 필드만, 메시지·스택·SQL·내부 경로·비밀값 미노출, HTTP 상태·traceId 일치. 상세는 api-contract.md 8절 |
| V16 | OPS01~OPS05 | 외부 포트·관리 endpoint·DB runtime DDL·TLS/hostname 오류 검사 | 비허용 경로·권한·인증서 거부, liveness 비밀 미노출 |
| V17 | OPS04, CI03 | image/IaC 검사와 실제 non-root·capability·mount·자원 제한 확인 | socket/privileged/내장 키 없음, 필요한 기능 정상 |
| V18 | OPS05, OPS06, CFG04 | 격리 환경 백업 복구·시크릿 회전·인증서 갱신·경보 시험 | 데이터 복원·이전 자격 폐기·경보/대응 런북 동작 |
| V19 | CI01~CI05, SEC09 | 외부 PR·신뢰 ref·artifact/cache 경로·배포 권한 검토 | PR 시크릿/운영 접근 불가, 검증 artifact만 배포 |
| V20 | SEC10, CI04 | SAST·SCA·secret·DAST 및 해당 image/IaC 검사 | 아래 finding 관문 만족, 검사 실패/미실행을 성공으로 처리하지 않음 |
| V21 | API06, SEC12 | 조건부 기능 사용 여부 확인·해당 공격/실패 시험 | 미도입은 코드/경로 미존재 증거. 도입은 요구사항별 시험 필수 |
| V22 | ENG03~ENG10 | 실제 formatter·lint·타입·관련 회귀·접근성 검토 | 공통 규칙·화면 상태·오류·명명·DB 변경 안전성 계약 충족 |

~ 표기는 포함된 모든 ID를 뜻한다. 시험 코드는 기능의 지속적 회귀 보호가 있는 경우만 추가하고 같은 동작을 중복 테스트하지 않는다. DB를 변경하는 시험·앱 시작·migration은 [DB 실행 규칙](../../common-rules/docs/conventions/common/database-access.md)의 승인 범위 안에서만 실행한다.

## DAST 범위

ZAP 등으로 익명 baseline/passive 검사와 로그인된 역할별 API·화면 검사를 구분한다. 전체 route 목록·OpenAPI·로그인 세션/CSRF 갱신을 제공해 실제 탐색 범위를 기록한다. 익명 스캔만으로 인증 영역 통과를 선언하지 않는다.

active scan·brute force·업로드 악성 payload·DoS는 사용자가 허가한 격리 대상만 사용한다. 로그아웃·메일 발송·계정 잠금·외부 결제·실데이터 변경 영향이 있는 경로는 범위를 명세한다. 제외한 경로는 수동 검증 또는 미검증 상태로 남긴다.

## finding 처리와 차단 기준

프로젝트 목표: 승인한 검사 범위의 미조치 유효 finding 0건. 스캐너 경고 0개 자체를 안전 보장으로 쓰지 않는다.

| 상태 | 처리 |
| --- | --- |
| Critical/High 또는 실제 악용 가능한 비밀 노출·권한 우회 | merge·배포 차단, 원인 수정/폐기·재검증. 일반 위험 허용으로 통과시키지 않음 |
| Medium/Low 유효 finding | 기본 차단. 수정 또는 아래 명시적 기한부 예외 필요 |
| Informational | 개별 의미·영향 분류. 수용/미적용 이유 기록, 등급 전체 제외 금지 |
| 오탐 | rule·경로·버전·재현 근거, 독립 검토 결과를 기록. 최소 suppress |
| 검사 실패/누락·접근 불가 | 미검증. 성공·N/A로 대체 금지 |
| 생성 JS·전이 의존성·이미지 | 배포 범위이면 검사 대상. 원본·의존성·빌드 정책에서 수정, 산출물 일괄 제외 금지 |

예외 양식: ID / 요구사항·rule / 정확한 경로·환경 / 위험·재현 / 수정 불가 이유 / 보완 통제 / 승인자 / 만료일 / 담당 / 재검토 조건 / 연결 증거.
운영 사용자 또는 위험 책임자가 명시적으로 승인해야 위험 허용이다. 구현자가 임의 승인하지 않는다. 예외가 있으면 “취약점 0건”이 아니라 열린 위험 수와 상태를 보고한다.

## 완료 관문

1. 관련 D 결정 해소, 버전·설정·권한·신뢰 경계 계약 확정.
2. 모든 요구사항 구현 위치 연결 또는 근거 있는 N/A. 조건부 기능 누락과 미도입 구분.
3. 필수 검사 실행·실패 해결, 실제 HTTPS 진입점과 브라우저에서 보안/기능 통과.
4. 예외·미확인 범위·검사 시각·도구 버전·artifact 고정, 운영 런북·갱신 책임 확정.
5. 새 릴리스·주요 업그레이드·인증/배포 변경·보안 권고·예외 만료 때 영향 항목 재검증.

공통 checklist 대응: 착수→decisions/baseline, 프론트→frontend/headers, API·배치→backend, DB·서버→infrastructure, CI→supply-chain, 완료→이 문서. 기준: [WSTG](https://owasp.org/projects/web-security-testing-guide), [공통 checklist](../../common-rules/docs/conventions/security/setup-checklist.md).

## 추가 필수 시나리오 0.2

[상세 시험](test-cases.md)의 V23~V36을 위 표와 함께 적용한다. 입력 해석, 필드 권한, 동시성, 세션 통제, MFA/복구, 암호화, 데이터 보호, 감사 무결성, 자원·패치, HTTP 노출, 표준 차이, JWT/OIDC·추가 기능, 공급망 증거를 검증한다.

ASVS 분류 345개는 [조항 대조표](asvs-mapping.md)를 기준으로 증거를 연결한다. 문서 검토 결과는 [검토 보고](review-report.md)에 있으며 36개 동작 시험의 실행 결과와 다르다. 전체 L3나 미선택 제품의 적합성을 이번 검토 통과로 간주하지 않는다.

## 공유 세션·환경 독립성 추가 기준

[인증 기반](../authentication-foundation.md)의 AF01~AF08을 관련 공통·provider 시험으로 연결한다. 기존 V01~V36을 대체하지 않는다. Docker 없는 호스트에서 외부 격리 DB/Redis로 실행하는 이식 시험도 [로컬 환경](../local-environment.md)의 완료 기준에 포함한다. 모두 실행 전 계획이다.
