# 프론트엔드 기능완료표

기준: [frontend-foundation](../docs/implementation/frontend-foundation.md). 경로는 `frontend/` 기준이며 예제 주소는 local 빌드 기준이다. `verified`는 적힌 검증 범위가 통과한 상태, `implemented`는 코드가 있으나 필수 검증이 남은 상태, `dependency-pending`은 실제 서버·승인된 통합 시험에 의존하는 미완료다. 전체 포함 항목이 verified가 되기 전에는 전체 완료가 아니다.

## 실행 증거

### 파일 연결·마감 검수 — 2026-10-30

실제 파일 스키마로43파일을 생성하고 업로드/목록/바이너리 다운로드/삭제를 연결했습니다. 승인된 전용 서버에서 PNG/JPEG·65코드포인트 파일명201, 목록200, 바이트/헤더·CSRF 누락403·정상 DELETE204·실제 UI 삭제/404 진단 ID를 확인했습니다. 검수 사본의 합성 선택은 실제 Axios POST로 저장·목록 갱신까지 통과했으며 OS 파일 선택과 구분합니다. [SECFILE.md](SECFILE.md)가 파일 작업의 상세 실행 범위·manifest·수동 잔여를 소유합니다.

현재 기존54개 중43 verified/11 implemented입니다. EX07의 오래된 I1 대기는 실제 조회·캐시 무효화/재조회 증거로 해소했습니다. 나머지는 아래 실제 한계를 유지하며 소스 미구현과 혼동하지 않습니다.

| 항목                 | 남긴 이유와 현재 증거                                                                                                                                                                 |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| LIB03/FV02/FV08/EX10 | 파일 mutation201/204와 인증17개·진행 취소/늦은 응답 회귀는 완료. 새 비밀번호 실제 변경은 이번 마감에서 불필요하므로 실행하지 않았으며 브라우저 정책상 새 자격 입력은 사용자 직접 단계 |
| LIB06/EX09           | 로그인·RHF 오류 요약·공통 안전 fieldErrors 변환 구현. 실제 서버가 돌려주는 필드 오류를 UI에 재현하는 후속 증거는 별도. 새 서버 계약을 가짜 성공으로 바꾸지 않음                       |
| EX03/FV03/FV07       | 폼/컨트롤 옵션과 오류·초기화는 통과. native beforeunload 취소/동의·새로고침/실제200% 확대의 도구 한계 유지                                                                            |
| FV09                 | 실제 로그아웃 데이터 폐기와 파일 인증 전환·취소/늦은 응답 차단을 보완. 전체 인증 동시 만료를 브라우저에서 시간 제어하는 범위는 응답 대역17개와 구분                                   |
| FV11                 | nonce/CSP/no-store/인라인 스타일·React 출력·URL 경계 검수 완료. nonce 없는 공격 코드 실행 차단과 실개인정보 경계 전체 시험은 완료로 확대하지 않음                                     |

### API 생성 기본 경로 수정 — 2026-10-30

- `npm run api:gen`은 환경변수 없이 설정 파일 기준 `../docs/api/openapi.json`을 사용한다. `OPENAPI_SPEC_PATH`는 다른 로컬 명세를 지정하는 선택 항목이며, 없는 파일을 지정하면 생성 전에 명확히 실패한다.
- 기본 입력과 명시 입력 모두 생성 성공, 없는 명시 경로는 종료 코드 1과 생성물 36개 해시 유지로 확인했다. 재생성 직후 30개 파일은 Orval 원시 서식 때문에 해시가 달랐지만 기존 Prettier 서식 적용 뒤 36개 모두 이전 SHA-256과 같았다. 생성 코드·API 계약 변경은 없다. typecheck와 변경 파일 포맷 검사를 통과했다.

전체 UI 정리(UIREF01~14)의 구현과 시각 검수는 [UI 점검표](UI-REVIEW.md)에서 확인합니다. 이 문서의 기존 54개 기능 검증 상태는 화면 스타일 변경만으로 올리지 않습니다.

2026-10-30 추가 수정(UIREF15~19): 강조색·로딩 간격·라디오·검색 선택 구역과 달력 선택기의 구현·실화면 검증도 같은 UI 점검표에서 구분합니다. 날짜 입력의 기존 API와 UTC/서울 변환은 유지합니다.

UIREF16 후속으로 `loading={false}`의 빈 슬롯 때문에 제출 버튼 문구가 치우치던 결함을 수정했습니다. 실제 문구 중심·진행 전후 폭·접근 이름과 PC/모바일 이미지는 UI 점검표의 버튼 중앙 정렬 절에 기록합니다.

UIOPT01~09의 기존 컨트롤 보완·라디오 내부 원12px·작업별 잠금은 [공통 컨트롤 보완표](UI-OPTIONS.md)에서 별도로 기록합니다. 이 구현으로 기존54개 기능의 검증 상태를 자동 변경하지 않습니다.

### I1-1 — 승인된 합성 환경의 첫 실제 브라우저 연동

- 기존 3000 개발 서버를 보존하고 별도 검증 사본에서 local production을 webpack으로 빌드해 13000에 실행했다. 최초 `src` 91개 파일의 SHA-256 차이 0, upstream은 18080, public origin은 13000임을 확인했다. 기본 페이지 200·no-store·script nonce·strict style CSP가 통과했다. 이 검증은 승인된 격리 PostgreSQL/Redis와 합성 계정만 사용했다.
- 일반 사용자 로그인 → 내 프로필/부서 조회, 관리자 전용 메뉴 숨김, `/users` 직접 접근 권한 차단이 통과했다. 새 탭 `/me`에서 실제 쿠키 세션이 복원됐고, 원래 탭 로그아웃 뒤 두 탭 모두 개인정보가 제거됐다. HttpOnly/SameSite 등 쿠키 속성 자체를 브라우저로 열람·검증한 것은 아니다.
- 관리자 로그인 → 사용자 목록/이름 검색 → 상세 프로필 → 목록 복귀 시 검색어·결과 유지가 통과했다. 접근/보안 이력은 명시 조회 뒤 실제 행·사건 코드·서울 밀리초 시각·진단 ID가 표시됐다. 접근 이력 11건에서 size10 → 1/2 → 다음 → 2/2(1행)을 확인했다. 합성 데이터에는 큰 ID와 복수 부서가 없어 해당 실응답 조합은 미검증이다.
- 페이지 크기 변경 중 인증 저장소 일시 장애 문구가 두 번 표시됐고 명시 재시도 후 회복됐다. 이후 보안 이력에 같은 시각의 `AUTH_STORE_FAILURE`가 보였다. 서버 원문·스택은 노출되지 않았다. 목록 오류가 진단 ID를 누락하던 프론트 표시를 발견해 `ErrorState`/Grid·프로필·인증 Guard에 정규화된 `ApiError.traceId` 전달을 추가했다. 이 수정 이후 재빌드 검증은 아래 후속 기록으로 구분한다.
- 일반 사용자/관리자 비밀번호·권한·데이터는 변경하지 않았다. refresh 만료 복구·동시 로그인 3정책·비밀번호/전체 로그아웃·실서버 field 오류·진행 요청 취소는 아직 브라우저 통합 완료가 아니다. FV08/FV09 전체를 verified로 올리지 않는다.
- I1-1 후속: 진단 ID 보완 후 local production 재빌드·typecheck·lint가 통과했다. format:check의 Grid 한 파일 형식 경고는 Prettier로 수정하고 변경 파일/문서 format 검사와 diff 공백 검사를 통과했다. 실제 미존재 큰 문자열 ID의 상세 조회에서 안전한 NOT_FOUND 문구와 UUID 진단 ID가 표시됐고 console warning/error는 0건이었다. 마지막 합성 관리자 세션도 로그아웃했다. 원래 개발 서버·backend·DB 설정은 수정하지 않았다.
- B6(3000 HMR 합성 UI): Checkbox/Switch 연동 Space, Radio 방향키·disabled 건너뛰기, MultiSelect와 그룹 동기화·전체 해제·검색 빈 결과, 전체 disabled/loading, 빈 Select를 확인했다. 큰 Dialog·dismissible=false의 Escape 차단·busy 중 종료 비활성·명시 종료 focus 복귀, ConfirmDialog 확인 완료/focus 복귀, Toast 명시 닫기와 자동 제거도 확인했다. production 추가 옵션 검증과 B5 native 이탈 확인 취소/동의·새로고침은 여전히 구분한다.

### I1-2 — HTTP 쿠키·CSRF·production 옵션 후속 검증

- 브라우저와 별도인 메모리 쿠키 jar로 13000의 실제 HTTP 응답을 검사했다. `local-access`, `local-refresh`, `local-csrf`는 HttpOnly·SameSite=Lax·Path=/·Domain 없음이며 loopback local HTTP에서 Secure가 없었다. login/logout의 삭제 쿠키도 같은 이름·Path·HttpOnly/Lax를 유지했다. 쿠키/CSRF 원문은 출력·파일 저장하지 않았다. HTTPS 공유 환경의 Secure 발급은 이번 local 시험으로 검증하지 않는다.
- csrf GET200 → login200 → csrf 재조회200 → 보호 me GET200 → 같은 CSRF 표현으로 후속 logout204 → csrf 재조회200 → HEAD me401이 통과했다. 로그인/로그아웃 때 CSRF 저장값 교체, 보호 GET에서 유지, 전 응답 no-store를 확인했다. 브라우저 실제 로그인/로그아웃 성공(I1-1) 및 수명주기 코드와 이 HTTP 속성 검사를 구분한다.
- 실제 invalid login 요청400에서 `VALIDATION_FAILED`, loginId/INVALID_FORMAT 및 password/INVALID_LENGTH·REQUIRED, UUID 진단 ID를 확인했다. 정상 로그인 폼의 동일 client 검증은 이 잘못된 요청을 전송 전에 차단하므로, 실제 서버 field 응답을 RHF에 표시하는 통합 경로는 아직 검증하지 않았다. 실제 로그인 실패 문구·입력 보존·진단 ID는 브라우저에서 확인했다.
- 소스 전체에서 localStorage/sessionStorage/IndexedDB/persist/document.cookie·cookieStore·Bearer/JWT decode 사용이 없음을 검색했다. Query·CSRF·폼은 메모리, Zustand는 UI만 보관한다. 브라우저 도구에는 저장소 inventory/cookie inspection API가 없어 브라우저 저장소 직접 열람 증거로 주장하지 않는다.
- B7: 13000 local production의 strict CSP에서 Checkbox/Switch Space, Radio 방향키/disabled 건너뛰기, 복수 선택 공유/해제, disabled/loading, 빈 Select, 큰 Dialog/dismissible=false/Escape/busy/명시 종료/focus, ConfirmDialog 진행 비활성/완료 focus, Toast 명시 닫기/자동 제거를 확인했다. 해당 콘솔 warning/error 0건이었다. B5 native 대화상자 도구 timeout은 반복하지 않았다.
- 후속 `npm run test:auth`의 기존 17개 세대/HEAD/권한/늦은 응답 사례를 재실행해 전부 PASS, console warning/error 0건이었다. 실제 서버 조회 완료가 빨라 진행 중 취소를 결정적으로 조작할 수 없고 브라우저 도구에 network delay/route interception API도 없어, 실서버 지연 취소는 대역 회귀와 구분해 미검증으로 유지한다.
- 비밀번호 변경은 DB 승인 부족이 아니라 `cua_repl` 도구 문서의 Computer Use Confirmation Policy → Hand-Off Required 조건으로 보류했다: “Ask the user to take over before any new credential is entered”, “have them complete the entry, confirmation, and submission steps themselves.” 합성 계정도 예외가 적혀 있지 않아 새 비밀번호 입력·확인·제출과 원래 비밀번호 복원을 자동 실행하지 않았다. 자동 승인 심사에서 거부된 사건은 없으며 명시 도구 정책에 따른 인계다. 브라우저 대신 다른 경로로 이 제한을 우회하지 않았다. 합성 계정 비밀번호는 변경하지 않았고 복원할 변경도 없다. 사용자 직접 변경/복원 검증이 남아 있다. 동시 로그인 3정책의 A/B 실제 결과는 backend 담당의 검증이며 프론트 브라우저 재실행 결과가 아니다.
- 진단 ID 제품 수정 파일은 `src/components/ui/Feedback.tsx`, `DataGrid.tsx`, `src/features/data/DirectoryScreen.tsx`, `AuditScreen.tsx`, `ProfilePanel.tsx`, `src/features/auth/AuthProvider.tsx`의 6개다. 전체 `npm run format:check`도 종료0이었다. 이 후속 단계에서는 제품 코드를 추가 변경하지 않았다.

### I1-3 — 자연 만료 복구·전체 로그아웃·사용자 전환

- 승인된 일반 사용자 브라우저 로그인은 05:38:30 UTC에 완료됐다. JWT/쿠키를 읽거나 조작하지 않고 실제 시간을 기다렸다. TTL 10분과 clock-skew 30초를 유지했으며, 05:49:46 UTC의 명시 부서 조회에서 TOKEN_EXPIRED GET401 → 잠금 후 HEAD401 → csrf GET200 → refresh POST200 → 부서 GET200을 서버의 경로/상태 기록과 실제 행 표시로 대조했다. 로그인 시간과 만료 후 흐름을 분리했으며 다른 담당자의 별도 refresh 요청을 이 결과로 세지 않았다.
- 갱신 후 새 탭 `/me`가 일반 사용자 프로필로 복원됐다. 기존 탭에서 전체 로그인 종료를 실행한 뒤 두 탭 모두 개인 프로필·권한·비밀번호 폼이 제거됐다. 비밀번호 변경은 하지 않았다.
- 관리자 실제 `/users`에서 ID 기본 순서 10001→10002, nameAsc 10002→10001, nameDesc 10001→10002를 확인했다. 이름 검색으로 관리자 한 행을 캐시한 뒤 logout → 일반 사용자 login → 브라우저 뒤로 이전 `/users` 복귀 시 관리자 행 대신 권한 오류가 표시됐다. 관리자 재로그인 뒤 검색어 없음·idAsc·size20·전체2행으로 초기화됐다. QueryClient 내부 직접 열람 증거 대신 실제 화면과 세대 회귀 증거를 구분한다.
- 실제 API 예제의 수동 프로필 Query가 fresh 캐시를 표시하고 로그아웃 뒤 이름 제거·캐시 없음·조회 비활성으로 전환됐다. 비활성 Query의 `isStale`만 사용해 무효화 후에도 fresh로 표시되던 문제를 발견해 `IntegrationExamples.tsx`에서 실제 query state의 isInvalidated를 useSyncExternalStore로 구독하도록 수정했다. 무효화의 자동 재조회 없음 정책은 유지했다. 이 추가 제품 수정은 위 진단 ID 6개 파일과 별도다.
- 최종 수정 production 브라우저에서 fresh → 무효화 → stale·fetchStatus idle → logout 뒤 이름 제거/캐시 없음/조회 비활성을 확인했고 console warning/error 0건이었다. local production webpack 빌드·TypeScript·lint·전체 format:check·diff 공백 검사가 통과했다. 한 번의 빌드는 격리 산출물 폴더 생성 EPERM으로 실패했으며 같은 빌드를 권한 경계 밖에서 재시도해 통과했다. 보안 설정이나 제품 코드는 이 파일 권한 오류 때문에 바꾸지 않았다. 최종 검증 사본과 실제 src hash 차이는 0개다. 3000과 13000 검증 서버는 유지하고 임시 인증 회귀 서버는 종료했다.
- 남은 검증은 비밀번호 변경/원복 사용자 인계, 실제 서버 field 응답의 RHF 표시, 실서버 지연 진행 조회 취소, B5 native 이탈 확인이다. 브라우저 두 탭의 동시 만료를 정확히 재현한 시험은 아니며 동시 refresh 제어는 기존 17개 회귀와 backend A/B 결과로 구분한다.

### 초기 검증 이력 — I1-1 이전

2026-10-29, Node 24.22.1 / npm 12.2.0:

- C1: `npm run typecheck`, `npm run lint`, `npm run format:check`, `npm run build:local`, `npm run build:prod` 종료 0. 생성 API 36개 파일을 포함한다.
- C2: `npm run test:foundation`의 9개 회귀 그룹 통과. 문자열 합계/반올림/0제수/입력·합계 폭/1000행/큰 ID/차트 좌표, UTC 세 형식·윤년·불가능 날짜·31일·서울 과거 DST와 중복/누락 시각·네 환경 공개 flag를 외부 연결 없이 검사한다.
- C3: 기존 외부 Node 입력 검사 55개 통과. 네 환경 선택·외부값 우선·프로파일 충돌·필수 origin/HTTPS·loopback upstream·공유 환경 개발 모드 거부. 실행 로그의 개인 경로·원문은 공개 저장소에 넣지 않는다.
- G1: `OPENAPI_SPEC_PATH`에 실제 `../backend/build/openapi/openapi.json`을 지정한 `npm run api:gen` 종료 0. 실제 Controller/springdoc 출력의 13개 path·14개 operation을 사용했다. backend 보완으로 입력이 갱신되는 동안에는 해당 실제 출력의 고정 사본으로 같은 입력을 두 번 생성해 36개 파일 hash 차이 0을 확인했다. GET Query/변경 Mutation은 생성기 기본 분류를 유지한다. 생성 코드는 직접 편집하지 않는다.
- B1: local production 브라우저에서 Select 열기/선택, 검색 선택 방향키·Enter, Grid 큰 ID/정밀 금액/서울 표시·행 선택, RHF 첫 오류 focus·수정 재검증·실패값 유지·중복 제출 방지·reset 확인/focus 복귀를 확인했다. 이 경로의 console CSP/hydration 오류 0건.
- B2: Dialog focus 진입·busy 중 Escape/닫기 차단·해제 뒤 Escape/focus 복귀, ConfirmDialog 취소 초기 focus, SidePanel, ECharts 막대/선/원형 탭과 원본 표를 production 브라우저에서 확인했다. 확인한 경로의 console warning/error 0건.
- C4: local/prod 각각 빌드 환경변수·선택 파일 없이 일반 `next start`와 `npm start`로 실행했다. 네 실행 모두 14개 예제 직접 URL(local 200/prod 404), prod 메뉴 제외, 엄격 CSP/script nonce·위조 nonce 교체·요청별 신규 nonce·no-store·404의 SSR 인라인 style 없음이 통과했다. API는 요청하지 않았다. 임의 포트 자식 실행 검사기는 준비 실패해 통과로 세지 않았고, 직접 시작한 두 고정 임시 포트에서 독립 HTTP 검사를 통과했다.
- B3: 최신 local production에서 버튼 8종 클릭·기본 type/disabled/loading, 입력 지우기/암호표시/HTML 문자열 escaping(생성 img 0), 숫자 '-'/'0.' 보존·범위/0제수·반올림 차이, 불가능 날짜/min-max/clear/역전 기간/UTC 밀리초, 검색 draft→Enter 적용·page0/표시1·size100/reset/접힘, 알림 4종 live·중복 합침·오류 retry, 탭 방향키/Tooltip 키보드 focus/메뉴 선택을 확인했다. Grid 정렬은 row-index 기준 -10.001→9.99→큰 금액, 단일 선택 교체·빈 결과도 확인했다.
- B4: 390×844 및 1280×900 뷰포트에서 모바일 메뉴 위치·Escape/focus 복귀·가로 넘침 없음, 차트 canvas 309→911→1071px resize, 3종 전환 후 canvas 1개·empty/loading/error/retry·정확한 값 표를 확인했다. Radix Checkbox/Radio/Switch/Tabs의 SSR 인라인 style은 ClientOnly 경계로 제거하고 실제 CSR 구현을 유지했다. CSP 전역 완화는 추가하지 않았다.
- B5: dirty 폼의 SPA 링크 이탈에서 확인 대화상자가 표시됐다. 그 뒤 브라우저 도구가 대화상자 응답 처리에서 timeout되어 취소/동의 후 복귀·새로고침 이탈·추가 옵션 전체 조합은 미검증이다. 발생 뒤 브라우저 검사를 통과로 추가하지 않았다. 임시 검증 탭의 명시 닫기/뷰포트 복원도 같은 도구 오류로 확인하지 못했다.
- C5: `git diff --check -- frontend` 통과. LIB/UI/EX/FV 54개 ID가 각각 정확히 한 번 기록되었다. HEAD 대비 lock의 기존 패키지/버전 변경·삭제는 0개이며 decimal.js 10.6.0만 추가되었다. 생성 API는 같은 실제 명세 사본으로 반복 생성 시 36개 파일 차이 0개다.
- I1 초기 보류(과거): 당시 실제 서버 통합은 미실행이었다. 현재 실행 범위는 I1-1/I1-2가 대체한다. 생성/타입/빌드 성공만을 통합 성공으로 취급하지 않는다.

## FV09-DELAY 응답 완료 순서 회귀

2026-10-29, 기존 `scripts/authRegression.tsx`와 `npm run test:auth`에 다음 5개 사례를 추가했다. 운영 인증 코드는 추가 문제 재현이 없어 수정하지 않았다.

| 사례                         | 제어한 완료 순서·실제 단언                                                                                                                                                   | 결과 |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- |
| logout 뒤 이전 성공          | A bootstrap의 me 응답 도착을 보류 → 실제 Provider logout으로 새 세대/anonymous 확인 → 보류한 A 성공 완료. 사용자 없음·세션 캐시 재주입 없음 유지                             | PASS |
| B login 뒤 A 성공            | A me 응답 본문을 A로 고정하고 보류 → 실제 Provider B login 완료 → A 성공 완료. 현재 사용자/현재 세대 Query는 B, 이전 세대 Query 재생성 없음                                  | PASS |
| 이전 finally와 새 bootFlight | A me 보류 → logout → 새 세대 B bootstrap의 me도 보류 → A 완료/finally → bootstrap 재호출. 반환 Promise가 진행 중 B Promise와 동일하며 HEAD/me 중복 없음 → B 완료/캐시 B 유지 | PASS |
| B login 뒤 이전 bootstrap401 | A bootstrap me 보류 → B login 완료 → A 응답을 401로 완료. 새 로그인·세대·캐시 B 유지                                                                                         | PASS |
| B login 뒤 이전 일반 API401  | A 인증 뒤 일반 API 요청 보류 → B login 완료 → 이전 401 완료. 실제 mutator는 REQUEST_CANCELLED로 격리하고 새 로그인/세대를 폐기하지 않음                                      | PASS |

요청 도착 Promise와 응답 release를 직접 제어했으며, 요청 시점의 사용자 본문을 먼저 고정했다. 다섯 사례 모두 이전 요청의 AbortSignal이 중단되지 않은 채 실제로 늦게 완료됐음을 검사했다. 따라서 단순 요청 취소로 통과한 결과가 아니다. 고정 sleep을 늘리지 않았으며 응답 완료 뒤에는 React 두 프레임만 반영하고 실제 QueryClient·Provider 상태를 검사했다. 기존 12개를 포함한 **17개 모두 PASS**, console warning/error 0건이었다. 실쿠키·다중 탭·동시 로그인 3정책·B5는 여전히 미검증이다.

## SPEC-001 최종 명세 동기화

- 안정 입력: `OPENAPI_SPEC_PATH=../docs/api/openapi.json`. 기존 `npm run api:gen` 실행 종료 0.
- 확인한 SHA-256: `8EA277C75F3EBAF7FF1B0CADA89C22C59557D400C64A4A52725926511CEDF5F4` — 인계 값과 일치한다.
- `src/api/generated/client.ts` 및 models를 재생성하고 파일명·SHA-256을 대조했다. 이전 36개 → 이후 36개, 변경/추가 **0개**, 삭제 **0개**였다. 기존 임시 backend 출력 대신 공개 안정 입력으로 재현됨을 확인했다.
- 공개 docs 원본·backend는 수정하지 않았다. 후속 `npm run typecheck`, `npm run lint`, `npm run format:check`는 모두 종료 0이었다. 생성 코드 무변경·운영 인증 코드 무수정으로 앱 빌드는 반복하지 않았다. R1의 local/prod 빌드 통과 이력과 구분하며, 실제 API 통신 성공을 뜻하지 않으므로 I1 범위는 유지한다.

## FRONT-FOUNDATION-R1 인증 회귀

- **FFR1-001 수정**: `src/features/auth/session.ts`의 잠금 후 HEAD와 `src/features/auth/AuthProvider.tsx`의 초기 HEAD 확인에서만 HTTP status 401로 갱신을 판단한다. HEAD 본문/오류 code에 의존하지 않는다. 일반 API의 원인불명 401 복구 범위는 바꾸지 않았다.
- **FFR1-002 수정**: Provider가 진행 중 bootstrap을 인증 세대별로 공유하고, 확인한 세대의 재호출은 checking 전환 없이 종료한다. 완료된 로그인도 확인 세대로 기록한다. 명시 재시도는 `bootstrap(true)`, 로그아웃/사용자 전환은 새 세대에서 다시 확인한다. 이전 세대의 finally는 새 세대의 진행 요청을 지우지 않는다. 권한 Guard를 제거하거나 시간 기반 캐시로 우회하지 않았다.
- **실제 회귀**: `npm run test:auth`로 임시 응답 대역 문서를 제공하고 `http://localhost:43175`의 **인증 회귀 실행** 버튼으로 실제 AuthProvider·UserDetailScreen·중첩/복수 PermissionGuard를 마운트했다. 기존 설치된 esbuild를 번들링에 사용하며 새 테스트 플랫폼·의존성은 설치하지 않았다. Axios 응답 adapter와 라우팅 비대상 Next Link만 대역한다. 요청별 대역 상태를 고정해 앞 사례의 지연 응답이 다음 사례에 섞이지 않도록 했다. 실제 backend/API/DB는 호출하지 않았다.
- **12개 PASS**: 초기/잠금 후 본문 없는 HEAD401→refresh 1/me 1, 초기 HEAD200→refresh 0, 잠금 후 HEAD200→refresh 0, refresh401→1회 실패 후 종료, 초기 HEAD503→refresh 0 및 명시 retry 성공, HEAD403→refresh 0, 잠금 후 HEAD503→refresh 0, 일반 API 원인불명401→refresh 0, 두 권한 상세/복수 가드/StrictMode에서 HEAD 1/me 1 유지, USER_READ 누락 거부, PERSONAL_INFO_READ 누락 거부, logout/새 세대에서 HEAD/me 각 1회 재검증 후 안정. 상태 도달 뒤 200ms 유지와 확인 세대 bootstrap 재호출에서도 인증 호출 수가 늘지 않는 것을 검사했다. console warning/error는 0건이었다.
- `test:auth`는 검사용 루프백 서버를 유지하는 명령이다. 결과 영역의 PASS/FAIL·호출 수를 확인한 뒤 Ctrl+C로 종료한다. 실제 쿠키·refresh 회전·다중 탭 서버 통합이나 기존 B5 이탈 확인 검증을 대체하지 않는다.
- **정적/빌드 회귀**: `npm run typecheck`, `npm run lint`, `npm run format:check`, `npm run build:local`, `npm run build:prod`가 모두 종료 0이었다. R1 당시 응답 대역 TSX의 타입은 prod 빌드에서도 검사했다. R1 당시 실제 서버/쿠키/다중 탭은 미실행이었다. 현재 실제 연동은 I1-1/I1-2, 최종 명세 동기화는 SPEC-001을 따른다.

## 라이브러리

| ID    | 실제 구현 파일                                                                             | 화면·명령·증거                                                       | 상태·남은 검증                                                                                              |
| ----- | ------------------------------------------------------------------------------------------ | -------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| LIB01 | `src/app`, `src/proxy.ts`, `next.config.ts`, `scripts/environment.mjs`                     | 전체 경로, C1/C3                                                     | verified — C1/C3/C4: local/prod 빌드·공통 start·메뉴/직접 URL·nonce 회귀                                    |
| LIB02 | `src/components`, `src/features`                                                           | 전체 예제, B1/B2                                                     | verified — 실제 컴포넌트·이벤트·SSR/CSR 및 반응형 대표 조작, B1~B4                                          |
| LIB03 | `src/api/generated/client.ts`, `src/features/data/*`, `src/features/auth/AuthProvider.tsx` | `/users`, `/departments`, `/audits/*`, `/me`, `/examples/api`, G1/C1 | implemented — I1-1 실제 Query 조회·캐시 연결 통과; 비밀번호 Mutation 사용자 인계·진행 취소 실서버 증거 남음 |
| LIB04 | `src/api/client.ts`, `src/features/auth/session.ts`                                        | 생성 mutator·쿠키·CSRF·timeout·AbortSignal, C1                       | verified — I1-1~3 실제 HTTP·CSRF·정제 실패·자연 만료 refresh 복구                                           |
| LIB05 | `src/stores/UiStore.tsx`, `src/components/layout/AppShell.tsx`                             | 메뉴 접힘·모바일·목록 복귀 조건, C1                                  | verified — B4 메뉴·I1-1 목록 복귀·I1-3 사용자 전환 초기화/폐기 확인                                         |
| LIB06 | `src/features/examples/FormsExample.tsx`, `src/features/auth/AuthScreens.tsx`              | `/examples/forms`, `/login`, `/me`, B1                               | implemented — 실제 로그인·실패 진단 ID 통과; 서버 field의 RHF 표시 후속 검증                                |
| LIB07 | `src/features/data/contracts.ts`, `api/errors.ts`, 폼 schema                               | C1/C2/B1                                                             | verified — C1/C2/B1 및 I1-1 실제 응답 schema·정제 오류/진단 ID 확인                                         |
| LIB08 | `src/features/examples/FormsExample.tsx`, `src/features/auth/AuthScreens.tsx`              | zodResolver, B1/C1                                                   | verified — 합성 register/Controller·오류 focus/재검증                                                       |
| LIB09 | `src/components/ui/{Selections,Overlays,Navigation}.tsx`                                   | 선택·팝업·탭 예제, B1/B2                                             | verified — C4 SSR·B1~B4/B7 production 선택/팝업 키보드·옵션·CSP 확인                                        |
| LIB10 | `src/components/ui/DataGrid.tsx`, `src/features/data/DirectoryScreen.tsx`                  | `/examples/grid`, `/users`, B1/G1/C1                                 | verified — I1-1/I1-3 실제 서버 조회·페이지·권한·허용 정렬 확인                                              |
| LIB11 | `src/components/ui/Chart.tsx`                                                              | `/examples/charts`, B2                                               | verified — B2/B4: 3종·resize·empty/loading/error/retry·대체 표·canvas 1개                                   |
| LIB12 | `src/lib/decimal.ts`, `src/features/examples/NumbersExample.tsx`, `DataGrid.tsx`           | `/examples/numbers`, C2                                              | verified — decimal.js 10.6.0/MIT 정확 버전 추가, 기존 버전 집합 유지                                        |
| LIB13 | `orval.config.ts`, `src/api/generated`                                                     | `npm run api:gen`, G1/C1                                             | verified — G1/SPEC-001 실제 생성·I1-1 실제 생성 Query 소비/통신 확인                                        |
| LIB14 | `tsconfig.json`, 전체 TS/TSX·생성 코드                                                     | C1                                                                   | verified — strict 타입 검사·빌드 통과                                                                       |
| LIB15 | `eslint.config.mjs`                                                                        | C1                                                                   | verified — lint 경고 0                                                                                      |
| LIB16 | `.prettierrc.json`, `package.json`                                                         | `npm run format:check`                                               | verified — C1/C5: 소스·설정·완료표·README·CHANGELOG 형식 검사 통과                                          |

## 공통 컨트롤

| ID   | 실제 파일                                            | 실행 예제                                               | 상태·증거/남은 검증                                                               |
| ---- | ---------------------------------------------------- | ------------------------------------------------------- | --------------------------------------------------------------------------------- |
| UI01 | `src/components/ui/Button.tsx`                       | `/examples/buttons`                                     | verified — C1/B3: 변형4·크기3·아이콘 클릭, 기본 type·disabled/loading 확인        |
| UI02 | `src/components/ui/Inputs.tsx`                       | `/examples/inputs`                                      | verified — B1/B3: ref focus·지우기·암호표시·readonly/disabled/error·HTML escaping |
| UI03 | `src/components/ui/Inputs.tsx`, `src/lib/decimal.ts` | `/examples/numbers`                                     | verified — C2/B3: string·편집 중 값·범위 오류·0제수·표시/계산 구분                |
| UI04 | `src/components/ui/Inputs.tsx`, `src/lib/dates.ts`   | `/examples/dates`                                       | verified — C2/B3: UTC 세 형식·IANA 왕복·불가능 날짜/min/max/clear/기간 오류       |
| UI05 | `src/components/ui/Selections.tsx`                   | `/examples/selections`                                  | verified — B1/B7 production Checkbox/Switch Space·Radio 방향키·disabled 건너뛰기  |
| UI06 | `src/components/ui/Selections.tsx`                   | `/examples/selections`                                  | verified — B1/B7 production 단일/검색·복수 공유·해제·empty/loading/disabled 확인  |
| UI07 | `src/components/ui/Form.tsx`                         | `/examples/forms`, `/examples/inputs`                   | verified — label/required/help/error·aria/ref·가로/세로·RHF, B1/C1                |
| UI08 | `src/components/ui/Overlays.tsx`                     | `/examples/overlays`                                    | verified — B2/B7 production 큰 Dialog·닫기 비허용·busy·확인 완료·focus 복귀       |
| UI09 | `src/components/ui/Feedback.tsx`                     | `/examples/feedback`                                    | verified — B3/B7 production tone/live·중복 합침·명시 닫기·자동 제거               |
| UI10 | `src/components/ui/Navigation.tsx`                   | `/examples/navigation`                                  | verified — B3: 탭 방향키·Tooltip 키보드 focus·접힘·메뉴 선택/비활성 확인          |
| UI11 | `src/components/ui/Feedback.tsx`                     | `/examples/feedback`                                    | verified — B3/B4: 글자 의미·Spinner/Skeleton·empty/error/retry 실행               |
| UI12 | `src/components/ui/Search.tsx`                       | `/examples/search`                                      | verified — B3: Enter 적용·reset/접힘·API page0/표시1·size100/기본20 확인          |
| UI13 | `src/components/ui/DataGrid.tsx`                     | `/examples/grid`, `/users`, `/departments`, `/audits/*` | verified — B1 client 정밀 표시/정렬·I1-1/3 실제 서버 페이지·허용 정렬             |
| UI14 | `src/components/ui/Chart.tsx`                        | `/examples/charts`                                      | verified — B2/B4: canvas 3종·resize·empty/loading/error/retry·정확한 값 표        |

## 메뉴·화면

메뉴·제목·현재 위치 원본은 `src/config/navigation.ts`, 예제 off 서버 경계는 `src/app/examples/layout.tsx`다.

| ID   | 실제 파일                                                                                               | 경로                                                                                           | 상태·증거/남은 검증                                                                                                      |
| ---- | ------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| EX01 | `src/app/page.tsx`, `src/components/layout/AppShell.tsx`                                                | `/`                                                                                            | verified — C1/C4/B4: registry 기능 목차·실행 안내·반응형 shell·예제 flag                                                 |
| EX02 | `src/features/examples/ControlExamples.tsx`                                                             | `/examples/buttons,inputs,selections,dates,overlays,feedback,navigation,search` (각 slug 별도) | verified — B1~B4/B7 주요 옵션·키보드·상태 production 확인, C4 8개 URL 통과                                               |
| EX03 | `src/features/examples/FormsExample.tsx`                                                                | `/examples/forms`                                                                              | implemented — B1 폼 검증 통과·B5 dirty 확인 표시, 취소/동의 복귀·새로고침 미검증                                         |
| EX04 | `src/features/examples/DataExamples.tsx`, `src/features/data/DirectoryScreen.tsx`                       | `/examples/grid`, `/users`                                                                     | verified — B1 client 및 I1-1/I1-3 실제 서버 조회·페이지·정렬 확인                                                        |
| EX05 | `src/features/examples/DataExamples.tsx`, `src/components/ui/Chart.tsx`                                 | `/examples/charts`                                                                             | verified — B2/B4: 3종·resize·상태·canvas 1개·대체 표                                                                     |
| EX06 | `src/features/examples/NumbersExample.tsx`, `src/lib/decimal.ts`                                        | `/examples/numbers`                                                                            | verified — 계산·반올림·큰 ID·표시/JSON 구분, C2; HTTP 왕복 아님                                                          |
| EX07 | `src/features/examples/IntegrationExamples.tsx`, `src/api/generated/client.ts`                          | `/examples/api`                                                                                | verified — I1-1~3 실제 수동 Query·캐시 무효화/재조회·합성 오류 구분, 마감13070 재확인. 실진행 취소 시간 제어 한계는 FV09 |
| EX08 | `src/features/examples/IntegrationExamples.tsx`, `src/features/auth/*`                                  | `/examples/auth`                                                                               | verified — I1-1~3 실제 로그인/logout·권한·다중 탭 폐기·자연 만료 복구                                                    |
| EX09 | `src/features/auth/AuthScreens.tsx`                                                                     | `/login`                                                                                       | implemented — I1-1/I1-2 실제 CSRF 로그인·실패 문구/진단 ID; 서버 field RHF 표시 남음                                     |
| EX10 | `src/features/auth/AuthScreens.tsx`, `src/features/data/ProfilePanel.tsx`                               | `/me`                                                                                          | implemented — 실제 프로필·현재/전체 logout 통과; 비밀번호 변경 사용자 인계                                               |
| EX11 | `src/features/data/DirectoryScreen.tsx`, `src/features/data/ProfilePanel.tsx`, `src/stores/UiStore.tsx` | `/users`, `/users/[userId]`                                                                    | verified — I1-1/3 실제 Query·권한·상세/복귀·정렬·사용자 전환 격리                                                        |
| EX12 | `src/features/data/DirectoryScreen.tsx`                                                                 | `/departments`                                                                                 | verified — I1-1 일반 사용자 실제 부서 Query·코드·서버 페이지 표시 확인                                                   |
| EX13 | `src/features/data/AuditScreen.tsx`                                                                     | `/audits/access`, `/audits/security`                                                           | verified — I1-1 실제 접근/보안 이력·명시 조회·서울 시각·코드/진단 ID·페이지 확인                                         |

## 완료 검증

| ID   | 실제 검증 위치·증거                                                             | 상태·남은 항목                                                                                                                                   |
| ---- | ------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| FV01 | package/lock, C1/C3, `scripts/verify-foundation.mjs`                            | verified — C1~C5: 버전/lock·타입/lint/format·local/prod·네 환경 입력·공통 start 회귀                                                             |
| FV02 | LIB01~16, G1, 실제 UI·생성 Hook imports                                         | implemented — 실제 의존성 import/실행·I1-1 생성 Query 통신 확인; Mutation 전체 통합 남음                                                         |
| FV03 | UI01~14, B1/B2                                                                  | implemented — B1~B4/B7 컨트롤 대표 옵션 통과; B5 native dirty 이탈 취소/동의 도구 제한                                                           |
| FV04 | registry·examples/layout·권한 경계, C1                                          | verified — C4 local/prod 메뉴·직접 URL/404 및 I1-1 실제 권한 경계 확인                                                                           |
| FV05 | `DataGrid.tsx`, `src/features/data/DirectoryScreen.tsx`, B1                     | verified — B1 client 정렬/정밀값·I1-1/3 실제 서버 페이지·허용 정렬                                                                               |
| FV06 | `Chart.tsx`, B2                                                                 | verified — B2/B4: 3종·resize·재마운트·canvas 중복 없음·대체 표                                                                                   |
| FV07 | `src/features/examples/FormsExample.tsx`, `src/features/auth/formErrors.ts`, B1 | implemented — B1 합성 폼·실제 로그인 실패 문구/진단 ID; 서버 field RHF·B5 이탈 도구 제한                                                         |
| FV08 | G1·생성 클라이언트·실제 화면 코드                                               | implemented — I1-1~3 실제 조회·로그인/refresh/logout·이력/오류 통과; password 사용자 인계                                                        |
| FV09 | auth generation·Web Locks·BroadcastChannel 코드                                 | implemented — 실제 쿠키/만료 복구/다중 탭 logout/권한/캐시 전환·대역17개 통과; 실진행취소·동시 만료 브라우저 제어 미검증                         |
| FV10 | `scripts/verify-foundation.mjs`, C2                                             | verified — 10개 순수 회귀 그룹, 실제 HTTP/Java 비교와 구분                                                                                       |
| FV11 | `src/proxy.ts`, B1/B2                                                           | implemented — C4 네 시작의 strict CSP/SSR/nonce·B3 escaping·B1~B4 라이브러리 조작 확인; nonce 없는 공격 코드 차단·실개인정보 경계 추가 시험 필요 |

포함 항목을 숨기거나 가짜 응답으로 완료 처리하지 않는다. 제외 범위는 셀 편집/일괄 저장·파일/Excel·Enterprise·관리 CRUD·가입/복구/MFA/실제 SSO·다크모드·새 테스트 플랫폼이다. 프론트 구현에서 DB/Redis/컨테이너·seed·배포·커밋/push는 실행하지 않았다.

## 파일 업로드 추가 범위

SECFILE04/06/11의 실제 API 연결·생성43파일·응답 대역/실행 검수와 잔여는 [파일 업로드 기록](SECFILE.md)을 참조합니다. 위54개 항목의 기존 제외 범위에 후속 승인된 작업을 별도로 추가했습니다. 파일 작업 성공만으로 기존 비밀번호·native 도구 한계 항목을 verified로 바꾸지 않습니다.
