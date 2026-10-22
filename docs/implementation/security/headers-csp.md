# 보안 헤더·CSP·캐시 계약

적용 기본안: D02·D03의 단일 출처, Next 동적 HTML, Spring /api/**. 설정은 아래 계약의 한 소유자에서 생성하고 최종 진입점에서 검증한다.

## HDR01 — 응답별 소유권

| 응답 | 담당 | 적용 |
| --- | --- | --- |
| Next HTML: 일반·로그인·보호·404·처리된 오류 | Next | 요청별 CSP, 문서 보안 헤더, 아래 캐시 |
| Spring JSON: 성공·검증·인증·권한·서버 오류 | Spring Security와 오류 경계 | 안전한 Content-Type, nosniff, no-store, API 방어 정책 |
| /_next/static/** 해시 자원 | Next/정적 제공 계층 | 정확한 MIME·nosniff·해시 자원 장기 캐시 |
| 프록시 자체 4xx/5xx HTML | 프록시 | 스크립트·인라인 스타일 없는 고정 오류 페이지와 default-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none' |
| HTTPS 전체 | TLS 종료 프록시 | HSTS와 HTTPS 진입 정책 |
| 업로드·다운로드 | API/파일 계층 | 별도 권한·MIME·attachment·nosniff·캐시 |

동일한 CSP·HSTS를 여러 계층이 append하지 않는다. 여러 CSP는 뒤 값이 앞 값을 덮는 방식이 아니므로 중복을 허용하지 않는다. 프록시가 애플리케이션 CSP를 일반 정적 정책으로 치환하면 실패다. Set-Cookie는 여러 독립 헤더를 보존한다.

## HDR02 — 운영 문서 응답 정책

아래 수치는 프로젝트 기본안이다. 외부 연동이 없는 최초 프로필이며 운영 도메인을 하드코딩하지 않는다.

| 헤더 | 값·정책 |
| --- | --- |
| Content-Security-Policy | HDR03의 요청별 nonce 정책, enforcing |
| X-Content-Type-Options | nosniff |
| X-Frame-Options | DENY. frame-ancestors 'none'과 일치 |
| Referrer-Policy | strict-origin-when-cross-origin |
| Permissions-Policy | camera=(), microphone=(), geolocation=(), payment=() |
| X-XSS-Protection | 레거시 필터는 0. XSS 방어 수단으로 사용하지 않음 |
| Strict-Transport-Security | 운영 HTTPS에서 max-age=31536000. HDR05 도입 순서 적용 |
| Server / X-Powered-By | 제품·버전 노출 제거 가능한 범위에서 제거, Next poweredByHeader 비활성화 |
| Content-Type | 실제 내용에 맞는 MIME. HTML은 text/html; charset=utf-8 |

COEP·CORP·COOP는 일괄 추가하지 않는다. 교차 출처 격리 또는 OAuth 팝업·외부 자원을 도입하면 기능 요구와 공격 경계를 확인하고 별도 결정한다. Expect-CT·HPKP는 기본 구현에 넣지 않는다. 스캐너가 요구한다는 이유만으로 모든 헤더를 추가하지 않는다.

근거: [OWASP HTTP Headers](https://cheatsheetseries.owasp.org/cheatsheets/HTTP_Headers_Cheat_Sheet.html), [Spring 헤더](https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html). 값과 담당 분리는 프로젝트 정책이다.

## HDR03 — 운영 CSP 기본안

NONCE는 설명용 표기다. 문자 그대로 배포하면 실패다. 헤더에는 한 줄로 직렬화한다.

    default-src 'self';
    script-src 'self' 'nonce-{NONCE}' 'strict-dynamic';
    script-src-attr 'none';
    style-src 'self' 'nonce-{NONCE}';
    style-src-attr 'none';
    img-src 'self';
    font-src 'self';
    connect-src 'self';
    media-src 'none';
    object-src 'none';
    base-uri 'none';
    frame-src 'none';
    frame-ancestors 'none';
    form-action 'self';
    worker-src 'none';
    manifest-src 'self';
    upgrade-insecure-requests;

| 경계 | 구현 기준 |
| --- | --- |
| nonce | 암호학적 난수로 최소 128비트 엔트로피, HTML 응답마다 신규 생성. 타임스탬프·고정값·환경변수 재사용 금지 |
| 신뢰 | 브라우저가 보낸 x-nonce·CSP 값을 제거/덮어쓰고 서버에서 생성. CSP 문자열에 사용자 제공 URL·헤더 직접 결합 금지 |
| Next 전달 | 렌더링 전 서버 요청 문맥과 최종 응답에 같은 CSP·nonce 전달. 해당 버전의 proxy/middleware API는 D01 뒤 고정 |
| 렌더링 | nonce HTML은 동적 렌더링, 공유 캐시·ISR·정적 export·PPR 사용 금지인 기본안 |
| 스크립트 | Next 초기화·hydration·라우트 전환 및 사용자 Script에 동일 nonce 전달. strict-dynamic 신뢰 스크립트의 URL sink도 검증 |
| 스타일 | CSS Modules/추출 CSS 기본. style 태그에 nonce가 있어도 style 속성·React style prop은 허용되지 않음 |
| 자원 | 외부 폰트·분석·CDN·iframe·worker·blob/data 기본 미허용. 필요 시 지시어별 정확한 출처·목적·검증을 추가 |
| 정적 경로 | 정적 자원은 nonce 생성 제외 가능. 단 HTML 오류나 정적 HTML이 그 제외 경로에서 제공되는지 검사 |
| prefetch·RSC | 샘플 matcher를 그대로 복사하지 않음. 실제 HTML이 전달되는 모든 경로에 적용하고 내부 요청은 HTML 정책 오염·캐시 혼합 여부 검증 |
| 강제 | 운영은 Content-Security-Policy 필수. Report-Only만 있으면 통과 불가 |

Next 공식 문서에서 nonce는 동적 렌더링과 연결되고 정적 캐시 전략에 제약을 준다. 본 기본안은 이를 수용한다. [Next CSP](https://nextjs.org/docs/app/guides/content-security-policy)

React style prop, CSS-in-JS, 이미지 최적화·플레이스홀더, third-party UI가 정책과 충돌하면 라이브러리·출력 방식을 먼저 변경한다. blanket unsafe-inline·unsafe-eval·https:·* 추가로 해결하지 않는다. 필요한 data: 이미지 등은 정확한 지시어와 사용 범위에 한정한 변경 기록이 필요하다.

## HDR04 — 개발·API·캐시의 차이

- local 개발 CSP만 필요한 eval·개발 style·루프백 WebSocket 출처를 허용한다. 실제 HMR 포트로 제한하고 운영 CSP 생성 함수·설정과 명확히 구분한다.
- local HTTP에는 upgrade-insecure-requests와 HSTS를 보내지 않는다. staging·production의 프로덕션 빌드는 완화값을 포함하면 실패다.
- JSON API는 application/json 또는 오류의 application/problem+json, nosniff, no-store를 적용한다. 방어용 CSP를 적용한다면 default-src 'none'; frame-ancestors 'none'; base-uri 'none'으로 유지한다. API 응답의 CSP가 Next HTML을 보호하지는 않는다.
- 보호·개인화 HTML, nonce HTML, 인증·현재 사용자·CSRF 응답은 Cache-Control: no-store. 인증 API·오류에도 동일하다. CDN 우회까지 확인한다.
- 개인 데이터가 든 RSC payload와 서버 fetch 결과도 공유 캐시에 넣지 않는다. public·s-maxage·공유 태그 캐시로 사용자 결과를 공유하지 않는다.
- 실제 콘텐츠 해시가 이름에 포함된 공개 정적 자원만 public, max-age=31536000, immutable 기본안. 해시 없는 자원에 같은 정책을 일괄 적용하지 않는다.
- framework가 관리하는 RSC 관련 Vary를 임의 덮어쓰지 않는다. 캐시 소유권은 [Next 헤더 계약](https://nextjs.org/docs/app/api-reference/config/next-config-js/headers)과 선택 버전을 대조한다.

## HDR05 — HSTS와 프록시 오류

운영 TLS·인증서 자동 갱신·모든 진입 호스트를 검증한 뒤 HSTS를 활성화한다. 최초 단계 max-age=300으로 검사하고 이후 31536000으로 올린다. 이 기간에는 도입 단계로 기록하며 최종 완료와 구분한다. includeSubDomains·preload는 모든 하위 도메인과 별도 서비스 HTTPS 확인 후 독립 결정한다.

프록시가 만든 413·429·502·503·504도 보안 헤더와 안전한 본문을 제공해야 한다. 프록시 제품을 정한 후 오류 응답까지 헤더가 적용되는 문법을 설계에 추가한다. 인증서 오류·TLS 연결 실패 자체에는 HTTP 헤더를 검사할 수 없음을 구분한다.

## HDR06 — 필수 검증

1. 생산 빌드·실제 진입 프록시에서 GET으로 일반 HTML·로그인·보호·404·오류를 수집. 헤더 하나씩 존재·값·중복 검사.
2. 같은 URL 두 번 조회해 서로 다른 nonce이며 각 응답 내부 script/style과 일치하는지 검사.
3. 임의 x-nonce 전달 시 무시되는지, 프리패치 후 직접 접속·새로고침에서도 정상인지 검사.
4. hydration·클라이언트 이동·폼·이미지·폰트·오류 화면이 CSP 위반 없이 작동하는지 확인.
5. 격리된 검증 화면에서 nonce 없는 스크립트·이벤트 속성·외부 프레임이 차단되는지 확인. 검증 화면을 운영 빌드에 포함하지 않음.
6. 사용자 A/B 응답과 CDN HIT/MISS를 검사해 nonce·개인 응답이 공유되지 않는지 확인.
7. 프록시 오류·API 오류·HTTP→HTTPS·보안 쿠키까지 확인. HEAD 검사만으로 통과시키지 않음.

공통 구현 근거: [OWASP CSP](https://cheatsheetseries.owasp.org/cheatsheets/Content_Security_Policy_Cheat_Sheet.html). 상세 시험은 [검증 기준](verification.md).

## ASVS 대조 보완 0.2

[ASVS 대조표](asvs-mapping.md)와 [GOV11](controls.md)을 적용한다. HSTS includeSubDomains가 없는 단계는 3.4.1의 L2 조건 충족으로 표시하지 않는다. 독립 앱·비신뢰 콘텐츠는 origin을 분리한다. COOP·preload·CSP 보고 등 선택강화와 공통 필수 헤더를 구분한다. 검증 V33을 추가한다.
