# 프론트엔드

[파일 업로드·출력 경계](SECFILE.md): `/examples/files`에서 로그인 후 이미지 업로드·내 목록·다운로드·삭제를 실제 API에 연결했습니다. PNG/JPEG·파일당5MiB·한 번에5개·동시 전송2개가 기본입니다. 전송100%와 서버 저장 완료를 구분하며 취소한 요청도 서버에 저장될 수 있습니다. 실행 검증과 수동 인계 범위는 해당 기록을 따릅니다.

Next App Router 기반 공통 UI·기능 카탈로그입니다. [기능완료표](FEATURES.md)에 LIB/UI/EX/FV별 구현·실행 증거·남은 검증을 기록합니다. [설계 안내](../docs/implementation/README.md)와 [버전표](../docs/implementation/stack-versions.md)를 참조합니다. 명령은 `frontend/`에서 실행합니다. Node **24.22.1**, npm **12.2.0**과 직접 의존성 정확 버전·단일 `package-lock.json`을 사용합니다.

## 빌드 환경과 실행 모드

목차(`/`)에서 네 분류의 예제를 고르세요. 화면에서 용도와 해볼 동작을 읽고 값을 바꾸면 결과를 확인할 수 있습니다. 사용 코드는 접힌 영역에 있습니다. 입력·폼·숫자·차트 예제는 서버에 저장하지 않습니다. 내 정보·사용자·부서·이력은 연결된 서버에서 실제 정보를 읽으므로 테스트 계정으로 로그인하세요.

전체 UI 정리와 PC·모바일 검수 결과는 [UI 점검표](UI-REVIEW.md)에 기록합니다. Pretendard v1.3.9를 직접 제공하며 [글꼴 출처와 OFL 고지](public/fonts/pretendard/README.md)를 함께 배포합니다. 외부 글꼴 CDN이나 새 UI 프레임워크는 사용하지 않습니다. 현재 기능 검증은43개 verified·11개 implemented이며 상세 한계는 FEATURES.md를 따릅니다. 이전 화면 정리 시점의42/12 기록은 이력으로 구분합니다.

날짜·시간 예제에서는 달력 아이콘으로 날짜를 고르거나 직접 입력할 수 있습니다. `DatePicker`·`DateTimePicker`와 기간 입력이 같은 선택기를 사용합니다. 날짜시간은 팝업에서 시·분·초·밀리초를 바꾼 뒤 확인으로 반영하며, 취소하면 원래 값을 유지합니다. 기존 `DateInput`·`DateTimeInput` 이름도 호환합니다. 날짜만 값은 시간대를 바꾸지 않고, 시각은 명시한 Asia/Seoul 또는 UTC와 서버 UTC 사이에서 변환합니다.

[공통 컨트롤 보완](UI-OPTIONS.md)은 선택 안내·검색 상태·복수 선택 태그/최대 수·일부 선택·입력 단위/글자 수·오류 요약·비동기 확인과 작업별 중복 실행 방지를 다룹니다. 버튼 메뉴의 두 저장 버튼은 같은 작업 키를 공유합니다. `useAction`은 동일 키를 즉시 잠그고 중복은 무시하며, 호출자가 완료 결과와 원래 실패를 처리합니다. 검색/저장 예제는 실제 서버 API를 호출하지 않습니다.

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

| 입력                 | 소비 경계               | 정책                                                                                                             |
| -------------------- | ----------------------- | ---------------------------------------------------------------------------------------------------------------- |
| APP_ENV              | 빌드·로컬 설정 검증     | local/dev/stg/prod. 명령이 선택하며 공개 가능한 환경명 하나만 Next가 정적 치환                                   |
| APP_EXAMPLES_ENABLED | 빌드·카탈로그 공개      | true/false만 허용. 기본 local=true, dev/stg/prod=false. 공개 boolean 정적 반영. false이면 메뉴 제외·직접 URL 404 |
| APP_PUBLIC_ORIGIN    | 빌드·로컬 입력 검증     | local은 루프백 기본값, dev/stg/prod는 외부 또는 선택 파일에 HTTPS origin 필수. 자격·path·query·fragment 금지     |
| LOCAL_API_ORIGIN     | local Next rewrite 생성 | 기본 loopback 8080, 브라우저 공개 설정으로 복사하지 않음. 공유 환경에는 upstream 없음                            |
| OPENAPI_SPEC_PATH    | Orval 실행              | 선택 사항. 다른 로컬 명세를 사용할 때 지정하며 기본값은 `../docs/api/openapi.json`                               |

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

Next config는 build/local에서만 입력을 검증합니다. 공개 환경명과 예제 공개 boolean만 `next.config.env`로 정적 치환하여 production Proxy의 CSP와 예제 노출이 시작 셸의 입력에 따라 바뀌지 않습니다. origin·LOCAL_API_ORIGIN·비밀·전체 환경 객체를 정적 치환하거나 JSON snapshot으로 저장하지 않습니다. local rewrite의 서버 전달 목적지는 Next 표준 서버 산출물에만 남습니다. dev/stg/prod의 `/api` 전달은 동일 출처 ingress가 소유하며 원격 인프라는 선구현하지 않았습니다.

NEXT_PUBLIC_는 빌드 시 고정된 공개값입니다. 새 설정을 추가할 때 현재 소비 기능과 **빌드 입력인지 실행 입력인지, 서버 전용인지 브라우저 공개인지**부터 정합니다. 업무용 runtime 환경 설정은 필요한 기능이 생길 때 별도 설계합니다. 인증·CSRF·오류 비노출 계약은 유지합니다. 저장소 지침 자동 생성을 막는 `agentRules: false`도 유지합니다.

## 코드 책임

환경 빌드/공통 시작 검증(2026-10-26): `typecheck`, `lint`, `format:check`, local/prod production 빌드 통과. 외부 연결 없는 입력 검사 55개로 네 환경 선택·외부값 우선·선택 파일 충돌·필수 origin/HTTPS·loopback API origin·공유 환경의 Next 개발 모드 거부를 확인했습니다. local API 대상을 임시 loopback 주소로 바꾼 서버 rewrite와 prod의 local rewrite 제외를 확인했습니다. 두 빌드 각각에서 빌드 환경변수·config/env 실제 파일 없이 `npm start`와 일반 `next start`를 임시 루프백 포트로 실행해 `/`·404의 CSP/nonce/no-store를 확인했습니다. local production은 엄격한 정책, prod에는 upgrade 정책이 적용됐습니다. 생성한 서버 PID 트리는 종료했고 backend/API/DB/Redis에는 요청하지 않았습니다. 브라우저 산출물과 HTML에서 합성 비밀값·내부 전달 대상이 미노출임을 확인했습니다. lockfile 내용은 동일하며 의존성 변경이 없어 npm ci는 반복하지 않았습니다. 실제 공유 서비스 접속·ingress·TLS·업무 인증 통과를 뜻하지 않습니다.

- `src/app`, `src/components/layout`, `src/config/navigation.ts`: 레이아웃·메뉴·제목·현재 위치, 카탈로그·로그인·내 정보·권한 보호 경로를 구성합니다. `src/features/data`는 생성 Hook의 사용자/부서 서버 페이지·프로필·이력 조회와 엄격한 응답 경계를 구현합니다.
- `src/providers/AppProviders.tsx`: 렌더링 트리마다 QueryClient를 생성합니다. 서버 전역 캐시·사용자 singleton을 두지 않으며 자동 Query/mutation 재시도는 껐습니다.
- `src/features/auth`: 현재 사용자는 Query에만 둡니다. 인증 세대 증가→조회 취소→캐시/CSRF 폐기, Web Locks·single-flight, 신호만 보내는 BroadcastChannel, HEAD me 확인과 제한된 refresh를 구현합니다. 보호 데이터가 없는 합성 예제는 인증 API를 자동 호출하지 않습니다. 파일 예제의 본인 목록은 인증을 확인합니다. 실제 인증·다중 탭 시험과 잔여는 FEATURES.md의 I1-1~3/FV09 기록을 따릅니다.
- `src/api/client.ts`: baseURL `/`, `/api` 경로 유지, credentials, 15초 Axios timeout, AbortSignal, 메모리 CSRF와 안전한 오류를 제공합니다. GET/HEAD의 TOKEN_EXPIRED만 한 번 복구하며 변경 요청은 자동 재전송하지 않습니다. CSRF 오류는 발급 endpoint만 재조회한 뒤 원래 오류를 돌려줍니다. Axios timeout은 서버 전체 요청 기한 보장이 아닙니다.
- `src/api/models.ts`: `ApiResponse<T>`, `PageData<T>` 계약. mutator는 AxiosResponse가 아닌 JSON 본문을 반환합니다. Query 데이터는 envelope이며 화면은 `query.data?.data`로 접근합니다. JSON content-type·traceId·envelope를 검사하며 업무 data 검증은 해당 명세/기능 책임입니다. 204/HEAD는 envelope 검사에서 제외합니다.
- `src/api/errors.ts`, `src/messages/messages.ts`: 승인 code·UUID v4 traceId·검증 식별자만 보관합니다. proxy HTML·네트워크·미등록 응답은 일반 문구로 변환하고 raw 응답/예외를 화면이나 console에 출력하지 않습니다. 폼은 fieldErrors의 field를 **해당 폼의 공개 필드 허용 목록**과 대조한 뒤 `fieldMessages`로 매핑해야 합니다.

서버 CSRF 발급 응답 `{ token, headerName: 'X-CSRF-TOKEN' }`을 탭 메모리에만 보관하며 로그인·로그아웃 후 재조회합니다. 인증 쿠키/JWT를 읽거나 해독하지 않으며 브라우저 영속 저장소에 인증 정보를 저장하지 않습니다.

Zustand는 Provider 범위 store factory로 사이드바 접힘·모바일 메뉴·목록 복귀용 검색/페이지/정렬 UI 조건을 관리합니다. 검색어는 탭 메모리에만 두고 인증 세대가 바뀌면 지웁니다. 서버 응답은 Query, 폼은 RHF가 소유합니다. RHF·Zod·resolvers는 합성 폼·로그인·비밀번호 변경에서 실제 연결합니다.

`src/components/ui`는 컨트롤·상태·Grid/Chart를, `src/styles/tokens.css`는 밝은 테마를 소유합니다. `src/lib/dates.ts`는 UTC 세 계약 형식과 IANA 서울 표시를 엄격하게 변환합니다. 과거 DST도 적용하며 누락/중복 현지 입력은 거부하므로 UTC로 입력해야 합니다. `src/lib/decimal.ts`는 precision 40/HALF_UP clone을 사용합니다. 입력은 18 유효자리·소수 6자리, 합계는 최대 1000행이며 혼합 규모의 합계 폭이 40자리를 넘을 가능성도 거부합니다. 차트 좌표에만 제한된 number 근삿값을 쓰고 정확한 값은 표에 남깁니다.

입력·숫자·차트 등의 예제는 공개 가능한 합성 데이터입니다. `/examples/api`의 수동 프로필 조회, `/examples/auth`의 인증 버튼, `/examples/files`의 업로드·본인 목록·다운로드·삭제는 실제 API에 연결됩니다. 합성 오류와 숫자 JSON 미리보기는 실제 통신의 증거가 아닙니다. `/users`·`/departments`·이력의 서버 페이지에는 현재 페이지의 client 정렬/필터를 적용하지 않습니다. 셀 편집·일괄 저장·Excel·Enterprise·실제 SSO·관리 CRUD·다크모드는 이번 범위 밖입니다.

## API 생성과 통합 시험

`orval.config.ts`는 Axios + TanStack Query v5 + 공통 mutator를 명시합니다. 실제 Controller/springdoc에서 DB 없이 내보낸 backend 명세로 다음 명령의 생성을 확인했습니다. GET 조회와 변경 mutation의 기본 분류를 유지하며 비밀번호를 QueryKey에 넣지 않습니다.

```powershell
npm run api:gen
```

생성 후 저장소에서 사용하는 서식으로 정리할 때는 `npx prettier --ignore-path ../.gitignore --write "src/api/generated/**/*.ts"`를 실행합니다. 생성 디렉터리는 일반 전체 포맷 검사에서 제외되어 있으므로 명시한 경로만 대상으로 합니다. 파일 API 반영본은16경로·18동작·32스키마와 생성43파일이며 해시·검증 범위는 SECFILE.md를 참조합니다.

기본 입력은 `orval.config.ts` 위치를 기준으로 저장소의 `docs/api/openapi.json`을 찾습니다. 다른 로컬 명세가 필요할 때만 `OPENAPI_SPEC_PATH`를 지정하세요. 상대 경로는 설정 파일 위치 기준이며 절대 경로도 사용할 수 있습니다. 지정한 파일이 없으면 기본 명세로 대체하지 않고 생성 전에 실패합니다.

```powershell
$env:OPENAPI_SPEC_PATH = '../docs/api/openapi.json'
npm run api:gen
Remove-Item Env:OPENAPI_SPEC_PATH
```

명세의 안정 입력은 실제 Controller/springdoc 출력이 반영된 공개 `docs/api/openapi.json`입니다. SPEC-001 입력 SHA-256·이전 생성 결과와의 차이는 [기능완료표](FEATURES.md)에 기록합니다. 명세의 paths는 `/api`를 포함하며 생성 시 제거하지 않습니다. `src/api/generated`는 생성 결과이며 직접 수정하지 않습니다. 원본 변경 후 같은 명령으로 재생성합니다. 인증 함수·조회 화면·API 예제가 생성 함수를 직접 소비합니다. fake spec/client·mock/MSW는 만들지 않았습니다. 실제 서버 연동 결과와 남은 검증은 기능완료표의 I1-1~I1-3에서 확인하세요. 쿠키·로그인·갱신·로그아웃·권한·목록 조회는 확인했고, 비밀번호 변경과 일부 오류·지연 요청 검증은 남아 있습니다. 코드 생성 성공만을 실제 연동 성공으로 표시하지 않습니다. Orval의 SWR/Angular/mock 출력 기능은 활성화하지 않습니다.

## CSP와 UI 검증

`src/proxy.ts`는 API·Next 정적 자원을 제외한 문서 경로에 CSPRNG 16바이트 nonce를 발급하고 위조 `x-nonce`/CSP를 덮어씁니다. 요청 CSP와 최종 응답 CSP를 맞추며 nonce HTML은 동적/no-store입니다. `useDocumentNonce`를 AG Grid styleNonce와 Radix Select viewport에 전달합니다.

local + Next development에서만 eval·개발 스타일·3000 루프백 WebSocket을 허용합니다. local production은 엄격한 스크립트/스타일 정책을 사용하되 HTTP용 upgrade/HSTS는 생략합니다. 운영 HSTS는 검증 후 TLS 진입점이 소유합니다. `img-src data:`만 HDR07의 AG Grid 아이콘 예외를 반영합니다.

Radix 팝업은 focus 관리를 유지하고 CSS 배경·CSSOM 스크롤 잠금을 사용합니다. SSR 인라인 style을 출력하는 Checkbox/Radio/Switch/Select/Tabs와 Grid는 ClientOnly 경계에서 실제 브라우저 구현을 마운트하며 서버에는 준비 상태를 표시합니다. ECharts는 기존 canvas에 init하고 richText tooltip을 사용하며 ResizeObserver와 dispose를 연결합니다. local/prod의 14개 예제 SSR·직접 URL·엄격 CSP/nonce/no-store 검사를 두 시작 명령에서 통과했습니다. production 브라우저에서 실행한 범위와 남은 옵션·접근성 검사는 [기능완료표](FEATURES.md)에 구분합니다. 글꼴은 Pretendard 고정 릴리스 파일을 자체 제공합니다. 외부 CDN·Enterprise·React chart wrapper는 추가하지 않았습니다.

```powershell
npm run test:foundation
npm run typecheck
npm run lint
npm run format:check
npm run build:local
```

`test:foundation`은 Node 내장 assert/TypeScript 실행으로 숫자·날짜·환경 공개 입력을 검사하며 외부 서비스에 접속하지 않습니다. 실제 로그인·비밀번호 변경은 대상·영향·복구 승인이 있는 별도 통합 시험입니다.

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
