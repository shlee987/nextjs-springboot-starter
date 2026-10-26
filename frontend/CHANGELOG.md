# 변경 이력

## 2026-10-26 — 1차 프론트엔드 기반

### 구성

- Node 24.22.1, npm 12.2.0, Next 16.4.3, React 19.3.1, TypeScript 6.0.3 기반 App Router·strict 타입·CSS Modules/일반 CSS를 구성했다.
- 확정 라이브러리와 정확 버전·단일 package-lock의 자동 integrity를 적용했다. npm의 strict peers/engine·save-exact를 유지하고 추가 UI wrapper·선택 테스트 도구는 도입하지 않았다.
- `npm run local`은 개인 PC HMR, `build:local/dev/stg/prod`는 환경별 production 빌드, `npm start`는 모든 성공한 빌드의 공통 실행 명령이다. dev는 공유 개발 환경을 뜻한다.
- 선택 환경 파일과 외부 빌드 입력을 검증하고 공개 환경명만 정적 반영한다. local의 `/api` 전달 대상은 검증된 loopback origin으로 변경할 수 있으며 공유 환경은 동일 출처 ingress 계약을 따른다.

### API·보안 기반

- 요청별 QueryClient Provider, 공통 Axios·응답 envelope·안전한 오류/메시지·timeout/취소·메모리 CSRF 연결점과 Orval Axios/Query v5 생성 설정을 준비했다.
- 요청마다 생성하는 nonce·CSP·no-store, 안전한 기본/404/오류 문서와 서버·브라우저 설정 경계를 적용했다. HMR 완화는 local의 Next development 모드에만 한정했다.
- 실제 환경 파일·의존성·빌드 산출물·로그를 공개 대상에서 제외하고 환경별 안전한 예제와 실행 절차를 제공했다.

### 확인한 결과

- npm ci 재현 설치, typecheck·lint·format 검사, local/prod production 빌드 통과. 설치 전환 시 기존 패키지명·버전 집합을 보존했으며 npm audit은 실행 시 알려진 취약점 0건이었다.
- 외부 연결 없는 빌드 입력 검사 55개 통과. 기존 API 경계 검사는 합성 adapter로 수행했으며 실제 인증·서버 API 시험과 구분했다.
- local/prod 산출물 각각을 빌드 환경변수·선택 파일 없이 npm start와 일반 Next start로 임시 루프백 포트에서 실행했다. 문서/404의 CSP·신규 nonce·no-store 및 prod upgrade 정책을 확인하고 생성 서버를 종료했다.
- local 전달 대상 변경·공유 빌드의 local rewrite 제외와 HTML/브라우저 산출물의 합성 비밀값·내부 전달 대상 미노출을 확인했다.
- npm 기본 정책으로 차단된 esbuild/unrs-resolver postinstall은 실행되지 않았다. 설치된 optional native 바이너리의 esbuild 변환·unrs 모듈 탐색·sharp 이미지 생성은 통과했다.

### 후속 검증

- 실제 로그인·계정 전환·CSRF 발급 흐름, backend API 통신과 실제 OpenAPI client 생성 검사는 미실행이다.
- 공통 컨트롤·그리드·차트의 SSR/CSR·접근성·HDR07 CSP 통합, 공유 환경의 외부 연동·ingress·TLS·운영 배포 검증은 후속이다.
- 이 기록은 전체 ASVS 충족이나 모든 보안 기능의 실행 통과를 뜻하지 않는다. 상세 범위는 [README](README.md)를 따른다.
