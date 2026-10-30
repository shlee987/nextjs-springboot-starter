# 변경 이력

## 2026-10-30 — 파일 실연동·마감 검수와 인증/날짜 보완

- 실제16경로·18동작·32스키마로43파일을 생성하고 업로드·본인 목록·바이너리 다운로드·삭제를 연결했다. JSON/Blob 파일7코드의 안전 문구/traceId/Retry-After와 Unicode120코드포인트 계약을 보완했다.
- 날짜 외부값 변경 후 부모 검증 상태 동기화와 파일 예제의 익명 인증 반복을 수정했다. Guard는 유지하고 파일 선택/미리보기·페이지·캐시만 세대별로 분리한다. 진행 요청 취소·늦은 응답/다운로드 저장 차단·새 사용자 초기화 회귀를 통과했다.
- 승인된 전용13070→18086에서 실제 저장/목록/바이트·헤더/CSRF 누락403/삭제204/로그아웃을 확인했다. 본인 생성 manifest5개를 모두 정리하고 임시 검수 페이지를 제거했다. OS 선택·저장 완료와 실네트워크 중도 취소 타이밍은 수동 한계로 구분한다. 기존54개 상태는43 verified/11 implemented이며 범위와 증거는 SECFILE.md·FEATURES.md를 따른다. 커밋/push는 하지 않았다.

## 2026-10-30 — 업로드 화면 디자인 보완

- 기본 파일 입력의 시각 표현을 업로드 아이콘·밝은 드롭 영역·공통 선택 버튼으로 교체하고 파일 행·선택 요약·작업 버튼을 PC/모바일에 맞게 정렬했다. 오류·전송·서버 처리·완료를 상태별로 구분하고 필수 취소 안내는 접어서 볼 수 있게 정리했다.
- Enter/Space 파일 선택 열기, 긴 파일명·좁은 화면·중앙 진행 표시와 기존 취소/재시도를 확인했다. 실제 서버 API 연결과 OS 파일 선택 제한은 [SECFILE.md](SECFILE.md)에 계속 구분한다.

## 2026-10-30 — 파일 업로드 공통 UI와 출력 경계

- 파일 선택/drop·파일별 오류·미리보기 정리·최대2개 병렬 전송·실제 진행 콜백·저장 대기·취소 종료·명시 재시도를 공통 FileUpload에 추가했다. 예제 메뉴는 서버 스키마 확정 전 연결 대기를 명시한다.
- React 텍스트 출력과 내부 링크를 유지하고 HTML 형태 파일명과 안전한 오류 출력을 응답 대역에서 확인했다. CSP는 로컬 이미지 미리보기의 img-src blob:만 허용하고 기존 script/style/connect 경계를 유지한다.
- 실제 서버 업로드·목록·바이너리 다운로드·삭제와 파일 선택 자동화 권한 제한은 [SECFILE.md](SECFILE.md)의 미확인 항목으로 구분한다.

## 2026-10-30 — 공통 컨트롤 옵션·작업별 실행 잠금

- 선택 안내·긴 항목 스크롤·제어된 검색 상태, 복수 선택 요약/태그/최대 수/검색 결과 전체 선택, Checkbox 일부 선택을 추가했다. 라디오 안쪽 원은12px로 키우고 흰 여백을 유지했다.
- 입력 단위·글자 수·Textarea 자동 높이·오류 요약 이동, 날짜 선택 제한/빠른 기간/외부 값 초기화, 확인 의도·비동기 실패 유지/성공 후 닫기, 빈 결과의 다음 행동과 재시도를 공통 구현과 예제에 연결했다.
- 동일 키를 즉시 잠그는 useAction을 폼 제출·확인·재시도에 연결했다. 두 버튼 공유·다른 키 병렬·중복 무시·취소 종료·인증 세대/화면 범위 격리와 오래된 finally를 검증했다. 기존 인증 refresh·API 오류/재시도 정책은 유지한다. 상세 구현·검증 범위는 [공통 컨트롤 보완표](UI-OPTIONS.md)를 따른다.

## 2026-10-30 — 강조색·선택 표시·달력 선택기

- 제출·로그인·조회·확인의 `loading={false}`에 빈 아이콘 슬롯이 남아 문구가 오른쪽으로 밀리던 문제를 수정했다. 아이콘 없는 버튼은 문구를 중앙에 두고, 진행 중 문구의 공간·접근 이름을 유지한 채 중앙 스피너를 겹친다. PC/모바일의 실제 문구 중심과 진행 전후 폭을 확인했다. 실제 아이콘이 있는 조합은 슬롯과 간격을 유지한다.
- 청색·적색·녹색을 선명한 토큰으로 바꾸고 hover/pressed/soft 상태를 맞췄다. 로딩 아이콘과 문구에 8px 간격을 주고, 버튼은 진행 표시 공간을 유지한다. 체크박스 스타일에서 라디오를 분리해 흰 원 안의 작은 점이 보이도록 했다. 검색 선택 팝업의 입력·결과·하단 구역을 나눴다.
- 공통 DatePicker·DateTimePicker를 추가하고 기존 날짜 입력·기간 검색에서 재사용한다. 직접 편집, 월/연도 이동, 오늘·선택 표시, 지우기, 키보드 이동과 포커스 복귀를 제공한다. 날짜시간의 임시 값은 확인으로 반영하고 취소 시 폐기하며 기존 초·밀리초를 보존한다. 날짜·UTC/서울·DST·범위 검증을 공유한다.
- PC·모바일 실화면과 UIREF15~19의 검사 결과는 [UI 점검표](UI-REVIEW.md)에 별도로 기록한다. 기존 54개 기능 상태와 API 생성 경로 수정은 유지한다.

## 2026-10-30 — API 생성 기본 명세 경로

- `api:gen`이 환경변수 없이 저장소의 `docs/api/openapi.json`을 사용하도록 수정했다. 경로는 설정 파일 기준이며 `OPENAPI_SPEC_PATH`는 선택 override다. 없는 명시 경로는 기본값으로 대체하지 않고 생성 전에 실패한다.
- 기본·명시 입력 생성 성공, 없는 경로 실패와 생성물 보존, typecheck·변경 파일 포맷을 확인했다. 원시 출력의 서식 차이를 기존 Prettier로 정리한 뒤 생성물 36개 해시가 모두 이전과 같았다.

## 2026-10-29 — I1-2/I1-3 인증 수명주기·캐시 표시

- 실제 쿠키 속성·CSRF 로그인/로그아웃 교체·보호 GET 뒤 후속 쓰기 유지, 자연 10분 만료와 시계 오차 허용 뒤 refresh 복구, 새 탭 복원·전체 로그아웃·사용자 전환 캐시 차단·서버 정렬을 확인했다. HTTP 속성 검사는 별도 메모리 cookie jar이며 브라우저 쿠키 직접 열람과 구분한다.
- API 예제의 비활성 Query가 무효화돼도 fresh로 표시되던 문제를 실제 query state의 isInvalidated 구독으로 보완했다. 자동 재조회 없음 정책을 유지한다.
- production 선택/팝업/Toast 대표 옵션과 기존 인증 회귀17개를 재확인했다. 과거 I1 미실행 기록과 현재 실행 결과를 구분하고 ID 표를 실제 통과 범위만 갱신했다. 비밀번호 변경은 브라우저 도구의 Hand-Off Required 정책으로 사용자 직접 입력·확인·제출을 기다리며, 합성 계정 비밀번호 변경/복원 실행은 없었다.

## 2026-10-29 — I1-1 실제 연동·진단 ID 표시

- 기존 3000 서버를 보존한 별도 local production 13000에서 승인된 합성 환경의 일반/관리자 로그인·프로필/부서/사용자 조회·권한 차단·상세 목록 복귀·접근/보안 이력·서버 페이지 이동을 검증했다. 실제 두 탭의 쿠키 세션 복원과 로그아웃 개인정보 제거도 확인했다.
- 인증 저장소 일시 장애와 명시 재시도 회복을 관찰했다. 공통 오류 화면이 정규화된 진단 ID를 표시하도록 Grid·프로필·인증 Guard의 전달을 보완했다. 비밀번호/권한 변경과 동시 정책 등 남은 범위는 기능완료표에 유지한다.
- 선택/팝업 옵션과 Toast 수동·자동 제거의 HMR 합성 UI 검증을 추가했다. native 이탈 확인 도구 제한은 해소됐다고 주장하지 않는다.

## 2026-10-29 — FV09-DELAY·SPEC-001 검증 보완

- 응답 도착·완료를 Promise gate로 제어하는 세대 전환 5개 회귀를 기존 응답 대역에 추가했다. logout 뒤 늦은 A 성공, B login 뒤 늦은 A 성공/401, 이전 finally와 진행 중 B bootstrap, 늦은 일반 API401을 실제 Provider·QueryClient에서 검사했다. 네트워크 요청이 abort되지 않은 늦은 완료를 확인했고 기존 사례를 포함한 17개 모두 PASS였다. 추가 제품 코드 문제는 재현되지 않아 운영 인증 코드는 수정하지 않았다.
- SPEC-001 안정 입력을 `../docs/api/openapi.json`으로 문서화하고 인계 SHA-256을 확인한 뒤 Orval을 실행했다. 생성 전후 36개 파일의 변경/추가/삭제는 0개였다. hash·각 사례·검증 범위는 [기능완료표](FEATURES.md)에 기록했다.
- 후속 typecheck·lint·format:check가 통과했다. 생성/운영 코드 무변경으로 앱 빌드는 반복하지 않았으며 R1의 local/prod 빌드 통과 기록과 구분한다.
- 실쿠키/다중 탭/동시 로그인 정책·실제 API/DB·B5는 미검증으로 유지한다. backend·공개 docs 원본·다른 영역 변경과 커밋/push는 수행하지 않았다.

## 2026-10-29 — FRONT-FOUNDATION-R1 인증 수정

- FFR1-001: 본문 없는 HEAD me의 401을 초기 확인과 잠금 후 재확인에서 HTTP status로 판단해 refresh를 한 번 허용했다. HEAD200은 갱신을 건너뛰고 HEAD403/503과 일반 API의 원인불명401은 자동 복구하지 않는다.
- FFR1-002: bootstrap 진행/완료 상태를 인증 세대별로 관리해 중첩·복수 권한 Guard의 재마운트가 전역 checking과 인증 조회 반복을 만들지 않도록 했다. 명시 retry·로그인 완료 확인·로그아웃/세대 전환 후 재검증과 이전 세대 응답 격리를 유지했다.
- 기존 Node·React·Axios·Query·esbuild만 사용하는 `npm run test:auth` 응답 대역을 추가했다. 실제 Provider·상세 화면·Guard를 마운트한 12개 사례에서 요청 수, 실패 후 반복 없음, StrictMode/권한 누락/새 세대 재검증을 확인했고 모두 PASS였다. 실제 backend/DB/API 쓰기·쿠키 통합 시험은 아니다.
- typecheck·lint·format:check·local/prod 빌드가 통과했다. 최종 backend spec 재생성/hash 동기화는 이번 수정에 포함하지 않았다.
- 재현 방법·각 사례의 결과와 남은 실서버 검증은 [기능완료표](FEATURES.md)의 FRONT-FOUNDATION-R1 절을 따른다.

## 2026-10-29 — 공통 기능·카탈로그와 생성 API 연결

- 밝은 CSS 토큰·반응형 레이아웃·메뉴 registry·Zustand UI/목록 복귀 조건, UI01~14 공통 컨트롤과 안전한 사용 코드 카탈로그를 구현했다. 예제 공개 flag는 local 기본 true/shared 기본 false이며 직접 URL도 차단한다.
- RHF/Zod/resolver 합성 폼, 문자열 Number/Amount 입력, UTC 세 날짜 형식과 IANA 서울 왕복, Community Grid의 client/server 분리, ECharts canvas 3종·richText·정확한 값 표를 연결했다.
- decimal.js 10.6.0(MIT)을 정확 버전으로 추가했다. 기존 패키지명/버전 집합은 보존하고 clone precision 40/HALF_UP·입력 18 유효자리/소수 6자리·1000행·합계 폭 제한을 적용했다. Node 내장 실행의 숫자/날짜/환경 회귀 9개 그룹이 통과했다.
- 실제 Controller/springdoc OpenAPI로 `npm run api:gen`을 실행했다. 생성 Axios/Query 함수·Hook을 인증, 사용자/부서 서버 페이지, 프로필, 접근/보안 이력, 수동 API 예제에서 소비한다. GET은 Query, 변경은 Mutation의 기본 분류를 유지해 비밀번호가 QueryKey에 들어가지 않도록 했다.
- 인증은 HttpOnly 쿠키·메모리 CSRF·HEAD 확인·제한 refresh·Web Locks/single-flight·신호만 공유하는 BroadcastChannel·인증 세대와 조회 취소/캐시 폐기를 구현했다. 안전한 조회만 한 번 재시도하며 변경 요청은 자동 재전송하지 않는다.
- 생성 코드 포함 타입/lint/local·prod production 빌드가 통과했다. local/prod 산출물을 빌드 환경변수 없이 일반 Next start와 npm start로 실행해 14개 예제 직접 URL·메뉴 flag·엄격 CSP/nonce/no-store·SSR 인라인 style 없음을 확인했다. production 브라우저에서 컨트롤·Grid·합성 폼·모달·차트/resize·모바일 대표 조작을 확인했다. 이탈 확인 대화상자 뒤의 브라우저 도구 timeout으로 취소/복귀와 추가 옵션 조합은 미검증이다. 전체 ID별 실제 검증과 남은 항목은 [기능완료표](FEATURES.md)가 소유한다.
- 실제 backend/DB/Redis·로그인·비밀번호 변경·다중 탭/동시 정책·운영 배포 통합 시험은 미실행이다. 코드 생성·합성 UI 검증을 실제 API 성공이나 전체 완료로 취급하지 않는다. 커밋/push는 수행하지 않았다.

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
