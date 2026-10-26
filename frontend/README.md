# 프론트엔드

Next App Router 기반 1차 세팅입니다. [설계 안내](../docs/implementation/README.md)와 [버전표](../docs/implementation/stack-versions.md)를 참조합니다. 명령은 `frontend/`에서 실행합니다. Node **24.22.1**, npm **12.2.0**과 직접 의존성 정확 버전·단일 `package-lock.json`을 사용합니다.

## 빌드 환경과 실행 모드

| 목적                 | 명령                                   | 배포 환경 APP_ENV  | Next 실행 모드 NODE_ENV  |
| -------------------- | -------------------------------------- | ------------------ | ------------------------ |
| 개인 PC HMR          | `npm run local`                        | local              | development              |
| 로컬 production 빌드 | `npm run build:local`, `npm run build` | local              | production               |
| 공유 개발 환경 빌드  | `npm run build:dev`                    | dev                | production               |
| 운영 전 검증 빌드    | `npm run build:stg`                    | stg                | production               |
| 운영 빌드            | `npm run build:prod`                   | prod               | production               |
| 성공한 빌드 실행     | `npm start`                            | 빌드에서 확정된 값 | production               |
| 타입 검사            | `npm run typecheck`                    | local 입력         | production (타입 생성용) |

배포 환경 `dev`는 **공유 개발 환경**만 뜻합니다. 로컬 HMR의 프레임워크 내부 명령은 `next dev`이고 NODE_ENV는 `development`입니다. 이는 APP_ENV=dev를 뜻하지 않습니다. IDE에서는 frontend를 작업 디렉터리로 지정하고 npm script `local`을 실행합니다. `npm run lint`, `npm run format:check`로 검사하고 `npm run format`으로 형식을 수정합니다.

```powershell
npm ci
# 선택사항: 개인 파일이 없을 때만 예제를 복사합니다.
Copy-Item config/env/.env.local.example config/env/.env.local
npm run local
```

local 진입은 기본 루프백 3000이며 브라우저 API 경로는 모든 환경에서 `/api`입니다. local만 Next가 IDE backend로 전달하고 기본 대상은 `http://127.0.0.1:8080`입니다. `LOCAL_API_ORIGIN`으로 loopback HTTP(S) origin을 바꿀 수 있습니다. 자격·추가 path·query·fragment·외부 호스트는 거부합니다. `/api` 접두사는 보존하며 CORS를 완화하지 않습니다. HMR CSP의 WebSocket 출처도 루프백 3000으로 제한합니다.

## build/local 입력

`config/env/.env.local`, `.env.dev`, `.env.stg`, `.env.prod` 중 명령이 선택한 파일 **하나만** Node 내장 API로 읽습니다. 각각의 `.example` 4개를 제공하며 실제 파일은 Git에서 제외합니다. 파일은 선택사항이고 외부 주입만으로도 빌드할 수 있습니다. 실제 비공개 파일은 도구가 이동·덮어쓰기·출력하지 않습니다.

우선순위는 **명령의 APP_ENV/NODE_ENV 선택 → 외부 프로세스 값 → 선택한 config/env 파일 → Next 기본 env 로드**입니다. 선택 파일의 APP_ENV/NODE_ENV가 명령과 충돌하면 실패합니다. 파일에는 NODE_ENV를 적지 않는 것을 권장합니다. Node 파서는 변수 보간을 하지 않습니다. 다른 프로파일 파일로 fallback하지 않습니다.

| 입력              | 소비 경계               | 정책                                                                                                         |
| ----------------- | ----------------------- | ------------------------------------------------------------------------------------------------------------ |
| APP_ENV           | 빌드·로컬 설정 검증     | local/dev/stg/prod. 명령이 선택하며 공개 가능한 환경명 하나만 Next가 정적 치환                               |
| APP_PUBLIC_ORIGIN | 빌드·로컬 입력 검증     | local은 루프백 기본값, dev/stg/prod는 외부 또는 선택 파일에 HTTPS origin 필수. 자격·path·query·fragment 금지 |
| LOCAL_API_ORIGIN  | local Next rewrite 생성 | 기본 loopback 8080, 브라우저 공개 설정으로 복사하지 않음. 공유 환경에는 upstream 없음                        |
| OPENAPI_SPEC_PATH | Orval 실행              | backend가 내보낸 로컬 명세 경로. API 생성 시에만 필요                                                        |

공유 환경의 필수 origin은 외부 주입/선택 파일에서 **Next 기본 env 로드 전에** 검증합니다. 루트 `.env.local`의 local 전용 값으로 공유 빌드의 필수값을 충족하지 않습니다. 루트 파일을 일괄 차단하지 않으며 나머지는 Next 기본 로드 규칙을 따릅니다. 개인 로컬 HMR 보조값은 `.env.development.local`에 둘 수 있습니다. `.env.local`은 production 빌드·실행에서도 읽히므로 공유/로컬 전용값을 섞어 두지 않습니다.

## 공통 production 시작

```powershell
# 해당 환경의 승인된 HTTPS origin을 빌드 입력으로 주입합니다.
$env:APP_PUBLIC_ORIGIN = 'https://app.example.invalid'
npm run build:prod
Remove-Item Env:APP_PUBLIC_ORIGIN
npm start
```

`npm start`는 별도 wrapper 없는 일반 `next start`입니다. 성공한 빌드와 Node 의존성만 있으면 APP_ENV·APP_PUBLIC_ORIGIN·LOCAL_API_ORIGIN 또는 config/env 파일 없이 실행됩니다. 기본 루프백 3000이며 시험용 포트는 `npm start -- --port 3100`처럼 전달합니다. `start:<환경>` 명령이나 자체 APP_ENV/BUILD_ID marker·설정 복원 파일은 없습니다. 환경과 설정을 바꾸면 **다시 빌드**하고 같은 산출물을 실행합니다. 이미 실행 중인 서버의 `.next`를 다른 빌드로 덮어쓰지 않습니다.

Next config는 build/local에서만 입력을 검증합니다. 공개 가능한 환경명 하나만 `next.config.env`로 정적 치환하여 production Proxy의 CSP가 시작 셸의 APP_ENV에 따라 바뀌지 않습니다. origin·LOCAL_API_ORIGIN·비밀·전체 환경 객체를 정적 치환하거나 JSON snapshot으로 저장하지 않습니다. local rewrite의 서버 전달 목적지는 Next 표준 서버 산출물에만 남습니다. dev/stg/prod의 `/api` 전달은 동일 출처 ingress가 소유하며 원격 인프라는 선구현하지 않았습니다.

NEXT_PUBLIC_는 빌드 시 고정된 공개값입니다. 새 설정을 추가할 때 현재 소비 기능과 **빌드 입력인지 실행 입력인지, 서버 전용인지 브라우저 공개인지**부터 정합니다. 업무용 runtime 환경 설정은 필요한 기능이 생길 때 별도 설계합니다. 인증·CSRF·오류 비노출 계약은 유지합니다. 저장소 지침 자동 생성을 막는 `agentRules: false`도 유지합니다.

## 코드 책임

환경 빌드/공통 시작 검증(2026-10-26): `typecheck`, `lint`, `format:check`, local/prod production 빌드 통과. 외부 연결 없는 입력 검사 55개로 네 환경 선택·외부값 우선·선택 파일 충돌·필수 origin/HTTPS·loopback API origin·공유 환경의 Next 개발 모드 거부를 확인했습니다. local API 대상을 임시 loopback 주소로 바꾼 서버 rewrite와 prod의 local rewrite 제외를 확인했습니다. 두 빌드 각각에서 빌드 환경변수·config/env 실제 파일 없이 `npm start`와 일반 `next start`를 임시 루프백 포트로 실행해 `/`·404의 CSP/nonce/no-store를 확인했습니다. local production은 엄격한 정책, prod에는 upgrade 정책이 적용됐습니다. 생성한 서버 PID 트리는 종료했고 backend/API/DB/Redis에는 요청하지 않았습니다. 브라우저 산출물과 HTML에서 합성 비밀값·내부 전달 대상이 미노출임을 확인했습니다. lockfile 내용은 동일하며 의존성 변경이 없어 npm ci는 반복하지 않았습니다. 실제 공유 서비스 접속·ingress·TLS·업무 인증 통과를 뜻하지 않습니다.

- `src/app`: 동적 기본 문서·CSS Modules·일반 CSS, 안전한 오류/404 문서. 로그인·업무 화면은 후속입니다.
- `src/providers/AppProviders.tsx`: 렌더링 트리마다 QueryClient를 생성합니다. 서버 전역 캐시·사용자 singleton을 두지 않습니다. 자동 Query/mutation 재시도는 껐습니다. `useResetSessionState`는 인증 전환 시 진행 조회 취소·캐시/CSRF 폐기의 연결점입니다. mutation 종료·사용자 전환·탭 간 동기화는 실제 인증 흐름에서 함께 설계합니다.
- `src/api/client.ts`: 브라우저 요청 한 경계. baseURL `/`, `/api` 경로 유지, credentials, 15초 Axios timeout, AbortSignal, 메모리 CSRF 토큰, 401 캐시 폐기 알림을 제공합니다. Axios timeout은 서버 전체 요청 기한 보장이 아닙니다. 변경 요청 자동 재전송·CSRF 자동 재발급·미정 로그인 경로 이동은 수행하지 않습니다.
- `src/api/models.ts`: `ApiResponse<T>`, `PageData<T>` 계약. mutator는 AxiosResponse가 아닌 JSON 본문을 반환합니다. Query 데이터는 envelope이며 화면은 `query.data?.data`로 접근합니다. JSON content-type·traceId·envelope를 검사하며 업무 data 검증은 해당 명세/기능 책임입니다. 204/HEAD는 envelope 검사에서 제외합니다.
- `src/api/errors.ts`, `src/messages/messages.ts`: 승인 code·UUID v4 traceId·검증 식별자만 보관합니다. proxy HTML·네트워크·미등록 응답은 일반 문구로 변환하고 raw 응답/예외를 화면이나 console에 출력하지 않습니다. 폼은 fieldErrors의 field를 **해당 폼의 공개 필드 허용 목록**과 대조한 뒤 `fieldMessages`로 매핑해야 합니다.

인증 단계에서 서버가 제공한 `{ token, headerName }`을 `setCsrfToken`에 연결합니다. 초기 지원 헤더는 Spring 세션 저장소의 `X-CSRF-TOKEN`입니다. 다른 헤더 계약은 인증 설계와 함께 변경합니다. 토큰은 탭 메모리에서만 유지하며 로그인·로그아웃 후 새로 발급받아야 합니다. 인증 쿠키를 읽거나 브라우저 영속 저장소에 인증 정보를 저장하지 않습니다.

Zustand는 여러 화면이 실제로 공유하는 UI 상태에만 사용합니다. 공유 상태가 아직 없어 가짜 전역 store를 만들지 않았습니다. 필요 시 Provider 범위의 store factory로 요청/사용자 경계를 유지합니다. 서버 응답은 Query, 폼은 RHF가 소유합니다. RHF·Zod·resolvers는 각 폼에서 `useForm`과 `zodResolver`로 연결하며 앱 전체 FormProvider나 업무 schema를 미리 만들지 않습니다.

## API 생성 대기

`orval.config.ts`는 Axios + TanStack Query v5 + 공통 mutator를 명시합니다. backend의 실제 명세가 제공되면 다음 순서로 진행합니다.

```powershell
$env:OPENAPI_SPEC_PATH = '<backend가 제공한 로컬 OpenAPI 파일>'
npm run api:generate
npm run typecheck
```

명세의 paths는 `/api`를 포함해야 하며 생성 시 제거하지 않습니다. 현재 명세·업무 endpoint가 없어서 생성 명령과 생성 결과 검사는 **미실행**입니다. generated 디렉터리·가짜 client·mock/MSW는 만들지 않았습니다. Orval 자체의 전이 어댑터 패키지는 설치 그래프에 포함되지만 SWR/Angular/mock 출력 기능을 활성화하지 않습니다.

## CSP와 UI 후속 조건

`src/proxy.ts`는 API·Next 정적 자원을 제외한 문서 경로에 CSPRNG 16바이트 nonce를 발급하고 위조 `x-nonce`/CSP를 덮어씁니다. 요청 CSP와 최종 응답 CSP를 맞추며 nonce HTML은 동적/no-store입니다. `useDocumentNonce`는 후속 AG Grid의 `styleNonce` 등에 연결할 공개 문서 nonce만 제공합니다.

local + Next development에서만 eval·개발 스타일·3000 루프백 WebSocket을 허용합니다. local production은 엄격한 스크립트/스타일 정책을 사용하되 HTTP용 upgrade/HSTS는 생략합니다. 운영 HSTS는 검증 후 TLS 진입점이 소유합니다. `img-src data:`만 HDR07의 AG Grid 아이콘 예외를 반영합니다.

Radix Portal/positioning/scroll-lock, AG Grid 편집/스크롤·nonce, ECharts canvas/richText tooltip의 실제 SSR/CSR·hydration·CSP 검사는 **후속 공통 UI 단계**입니다. 도구 설치만으로 호환 통과를 주장하지 않습니다. 외부 폰트·CDN·Enterprise·React chart wrapper는 추가하지 않았습니다.

## 설치 정책과 확인 결과 (2026-10-26)

사용자 결정에 따라 npm으로 전환했습니다. npm 12.2.0의 공식 Node 지원 범위는 `^22.22.2 || ^24.15.0 || >=26.0.0`이며 Node 24.22.1과 호환됩니다. `packageManager`·`engines`에 버전을 기록하고 시스템 도구를 자동 업그레이드하지 않습니다. [npm 12 설정](https://docs.npmjs.com/cli/v12/using-npm/config/)

`.npmrc`에는 npm에 유효한 `save-exact=true`, `strict-peer-deps=true`, `engine-strict=true`만 유지합니다. pnpm 전용 설정·잠금 파일·게시 대기/빌드 승인 목록은 제거했습니다. 의존성 lifecycle은 npm 12 기본 정책을 따릅니다. 현재 esbuild/unrs-resolver의 postinstall은 승인 목록이 없어 차단되며 경고를 기록합니다. 플랫폼별 optional 바이너리의 실제 동작과 lint/build를 검사하고, 별도 전체 허용·강제 설치·peer 무시 옵션을 추가하지 않습니다. 자동 integrity가 포함된 `package-lock.json`을 관리하고 재현 설치는 `npm ci`를 사용합니다. 수동 체크섬/버전별 예외 대장은 두지 않습니다.

직접 의존성 버전은 모두 유지했습니다. 기존 pnpm 설치 그래프와 npm lock의 패키지명·버전 집합은 584개로 일치합니다(플랫폼 optional 패키지 포함, 설치 배치·peer 연결 방식은 매니저별로 다름). 초기 해석에서 갱신된 전이 패치 3개는 기존 버전으로 복원하고 임시 overrides를 제거했습니다. npm의 정상 resolver로 lock을 생성했으며 영구 override·추가 의존성·예외 목록은 없습니다. ESLint 9.39.5 deprecated 경고는 기존 호환 버전 결정을 유지한 결과입니다. 취약점 검사는 `npm audit`으로 수행하고 `audit fix`로 자동 변경하지 않습니다.

전환 검증: Node 24.22.1/npm 12.2.0에서 `npm install`, `npm ci`, `npm run typecheck`, `npm run lint`, `npm run format:check`, `npm run build`를 통과했습니다. `npm ci` 전후 lock 내용이 같고 `npm ls --all`의 dependency problems는 없습니다. `npm audit --json`은 실행 시 취약점 0건입니다. 강제/peer 무시 옵션이나 `audit fix`는 사용하지 않았습니다.

설치 시 esbuild 0.28.2/unrs-resolver 1.12.2의 postinstall은 npm 기본 정책으로 차단되어 실행되지 않았습니다. 설치된 optional native 바이너리로 esbuild TypeScript 변환·unrs-resolver의 React 모듈 탐색·sharp 1×1 PNG 생성은 통과했습니다. 모든 플랫폼·향후 버전의 설치 스크립트 호환성을 보장하는 결과는 아니며, 필요성이 확인되면 그 패키지의 설치 정책만 별도로 검토합니다.

다음 보안 실행 결과는 npm 전환 전 기존 구현에서 확인한 기록입니다. 전환 시 해당 코드는 수정하지 않았으며 앱서버/API/DB/Redis/컨테이너를 실행하는 검사는 반복하지 않습니다.

- V02·FE03/API 계약 일부: 외부 임시 검사 41개 assertion 통과. 환경 누락·잘못된 HTTPS origin·공유 환경 개발 모드 거부, proxy HTML/미등록 오류 비노출, timeout/취소 분류, envelope 유지, CSRF 없는 변경 요청의 전송 전 거부, 401 알림/CSRF 폐기를 네트워크 없는 Axios adapter로 확인했습니다. 실제 서버 인증 시험은 아닙니다.
- V08/V09 일부: production GET 200/404, 128비트 요청 nonce 변경·위조 값 덮어쓰기·문서 script nonce 일치·no-store·X-Powered-By 없음 확인. 누락 정적 자원은 HTML이 아닌 404 text/plain 응답입니다. 브라우저 기본 문서에서 CSP/hydration warning/error가 없었습니다.
- APP_ENV=prod 실행에서도 eval/inline/WebSocket 완화 없음·upgrade 정책·보안 헤더·404 인라인 style 없음 확인. 기존 개발 명령의 local 응답에서는 필요한 개발 완화와 루프백 WebSocket만 확인했습니다. 실제 원격 prod 배포나 HMR 상호작용 전체 시험은 아닙니다.
- V11 일부: 합성 서버 canary를 빌드/실행에 주입해 기본 HTML과 `.next/static`에서 미노출을 확인했습니다. 브라우저 산출물에서 local backend origin이 없고 source map 0개임을 확인했습니다. 컨테이너/로그 수집기/운영 CDN 검사는 미실행입니다.
- 원격 진입점·HSTS·보호/로그인 화면·오류 유발 경로·사용자 A/B 캐시·nonce 없는 공격 코드 차단·공통 UI HDR07·실제 API 통신/생성 검사는 미실행입니다. V08~V12 전체·보안 전체·ASVS 전체 통과로 취급하지 않습니다.
