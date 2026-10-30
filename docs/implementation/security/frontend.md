# 프론트엔드 구현 기준

> 2026-10-29: 갱신 가능한 TOKEN_EXPIRED는 제한된 단일 갱신 후 처리하며, 폐기401·refresh401은 재로그인한다. 기존 401 즉시정리 문구보다 [프론트 인증 연결 계약](../backend-handoff.md)이 우선한다. 세션 CSRF가 아닌 쿠키 저장소+메모리 표현을 사용하며 일반 API는 HttpSession을 만들지 않는다.

## FE01 — 서버·브라우저 경계

- Next 서버 전용 설정·Spring 내부 URL·인증 관련 로직은 server-only 모듈에 둔다. 프론트에서 DB 접근·업무 권한 판정을 구현하지 않는다.
- NEXT_PUBLIC_ 값은 누구나 볼 수 있는 빌드 결과라고 취급한다. 시크릿을 넣지 않는다.
- Client Component·props·RSC payload·페이지 데이터·에러 메시지는 브라우저 공개 데이터다. 서버에서 조회했어도 최소 DTO만 전달한다.
- Spring API가 매 요청 권한을 검증한다. Next의 메뉴 숨김·리다이렉트·서버 컴포넌트 검사만으로 보호를 완료하지 않는다.
- Server Actions·Route Handler는 기본 미도입. 도입 시 공개 진입점으로 취급하고 입력·인증·권한·CSRF·Origin·속도·오류 계약을 별도 설계한다.
- 설정 전체·HTTP 응답 전체·쿠키 저장소를 JSON으로 직렬화해 props나 로그로 넘기지 않는다.

근거: [Next Data Security](https://nextjs.org/docs/app/guides/data-security). HTTP API 호출 경계를 선택하고 다른 데이터 접근 방식을 임의 병행하지 않는 프로젝트 설계다.

## FE02 — 출력과 외부 콘텐츠

React 기본 escaping을 유지한다. dangerouslySetInnerHTML·직접 innerHTML·사용자 입력 기반 스크립트 생성은 기본 금지다. HTML 편집 기능을 도입할 때만 정화 라이브러리·허용 태그/속성/URL 스킴·검증 payload를 명세한다. 정화 뒤 원문을 다시 삽입하지 않는다.

링크·리다이렉트·이미지·외부 요청은 값의 사용 문맥별로 스킴·목적지를 제한한다. javascript:·사용자 지정 data: HTML·임의 외부 로그인 복귀 URL은 거부한다. next/image 원격 패턴은 승인 호스트·경로로 제한하고 내부 주소를 대리 조회하는 설정을 허용하지 않는다.

외부 CDN·웹폰트·분석 스크립트는 기본 사용하지 않는다. 도입 시 CSP·데이터 전송·변조 영향·SRI 적용 가능성·장애 동작을 검토한다.

## FE03 — API 요청·세션·상태

- 공통 요청 경계 하나에서 API 기본 경로, credentials, CSRF, 타임아웃, 취소, 안전한 오류 변환을 관리한다. 브라우저 기본안은 같은 출처 /api이다.
- 인증 증명은 HttpOnly Access JWT/Refresh 쿠키이며 API에서 HttpSession 인증은 사용하지 않는다. access/refresh token을 localStorage·sessionStorage·URL·상태 영속화·분석 도구에 저장하지 않는다.
- CSRF 토큰은 인증 증명이 아니며 해당 탭 메모리에서만 관리하는 기본안이다. 로그인·로그아웃 후 새로 발급받는다. 세션 프로필 상세는 backend.md.
- 조회 로딩·빈 값·실패를 분리하고 401이면 보호 데이터를 비운 뒤 로그인 경로로 이동한다. 403을 빈 결과나 성공으로 바꾸지 않는다.
- 403을 자동 재로그인·무한 재시도로 숨기지 않는다. CSRF 재발급은 명시된 오류코드·횟수 제한으로만 하고 변경 요청의 자동 재전송은 기본 금지한다.
- 로그아웃·계정 전환 시 클라이언트 캐시와 민감한 상태를 비운다. 이전 사용자 정보가 뒤로가기·탭 전환으로 남는지 검증한다.
- 서버 fetch에는 명시적 캐시 계약, 타임아웃, HTTP 상태 검사를 적용한다. 내부 목적지는 설정값으로 고정하고 원래 Host를 upstream으로 사용하지 않는다.

## FE04 — UI·빌드 완료 기준

| 항목 | 통과 조건 |
| --- | --- |
| CSP 호환성 | [헤더 계약](headers-csp.md) 기준 생산 빌드에서 실제 기능·차단 동작 통과 |
| 타입·입력 | TypeScript strict 기본안. 외부 응답을 as 단언만으로 신뢰하지 않음. 필요한 경계 검증 |
| 접근성 | label·키보드·포커스·오류 안내·버튼/링크 의미, 비밀번호 관리자·붙여넣기 허용 |
| 오류 | 오류 경계와 비동기 처리 구분. 스택·내부 URL·raw 오류 응답을 UI에 노출하지 않음 |
| 배포 파일 | 브라우저 source map 기본 비공개. 오류 수집용이면 별도 접근 제한 저장소 |
| 비밀값 | HTML·JS·RSC·소스맵·정적 파일·브라우저 로그에 테스트용 canary 시크릿 미노출 |
| 보안 기본값 | 개발용 디버그·테스트 페이지·mock 인증이 운영 산출물에 없음 |

출력·검증 근거: [OWASP Input Validation](https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html). 위 UI·빌드 항목은 공통 규칙을 프로젝트에 구체화한 것이다.
