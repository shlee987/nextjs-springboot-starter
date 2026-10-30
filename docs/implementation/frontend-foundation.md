# 프론트 공통 기능·예제 카탈로그 최종 설계

기준일: 2026-10-30 / 버전: 1.1 / 상태: 공통 화면·라이브러리 사용 예제·후속 UI 보완 구현, 승인된 실제 파일 연결과 독립 코드 검토 완료. 도구·운영 검증의 한계는 모듈 기록을 따른다. 최신 실제 구현/검증과 남은 항목은 [기능표](../../frontend/FEATURES.md), [UI 옵션](../../frontend/UI-OPTIONS.md), [파일 기능](../../frontend/SECFILE.md)에서 확인한다. 과거 1차 설치 결과와 구분한다.

> 2026-10-29 후속: 전체 화면의 시각 정리·공통 컨트롤 통합·PC/모바일 배치는 [UI 정리 설계](frontend-ui-refinement.md)를 적용한다. 기존 기능·보안·검증 잔여 계약은 유지한다.

## 0. 목표와 완료 원칙

사용자의 목표는 업무 시스템 전체가 아니라, 자주 사용하는 공통 기능을 실제 구현하고 메뉴·예제 화면에서 기능과 사용법을 확인하는 재사용 기반이다. 1~4단계 기본안은 유지하고 세부 기본값은 설계 담당이 일반적인 형태로 구체화한다.

**의존성 설치만으로 완료하지 않는다.** 기본 채택 라이브러리마다 실제 사용 코드·실행 경로·예제 화면·검증 증거를 연결한다. 공통 컨트롤도 같은 기준으로 추적한다. export만 있거나 실행되지 않는 예제, 실제 API를 합성 결과로 대체한 화면은 완료가 아니다. 기능은 공통 구현을 예제에서 사용하며 예제 전용으로 다시 만들지 않는다.

라이브러리의 모든 기능/위젯·유료 기능을 구현한다는 뜻은 아니다. 아래 확정 공통 범위와 라이브러리별 대표 사용은 빠짐없이 제공한다. 범위 밖인 그리드 셀 편집/일괄저장, 파일·엑셀, 관리CRUD 전체, 가입·복구·MFA·실제SSO, 다크모드/테마편집기, Storybook·새 테스트플랫폼은 별도 설계 대상으로 명시하고 조용히 누락시키지 않는다.

관련 원본: [백엔드 API·인증 연결](backend-handoff.md), [JSON/날짜/숫자](backend-foundation.md), [인증](authentication-foundation.md), [CSP](security/headers-csp.md), [API 계약](api-contract.md), [선택 도구](stack-alternatives.md), [버전](stack-versions.md). 범용 common-rules는 변경하지 않는다.

## 1. 구조·화면·스타일 (프론트1단계)

- src/app은 라우팅/레이아웃, src/features는 기능별 화면·업무, src/components/ui는 공통 컨트롤, components/layout은 헤더/메뉴/본문, src/api는 기존 요청 경계/generated, hooks/stores/styles/messages는 역할별 공통 기능. 기존1차 구조를 재사용한다.
- 기본 페이지: 상단 프로젝트명·사용자·로그아웃, 접을 수 있는 왼쪽 메뉴, 제목·현재 위치, 본문. 로그인 레이아웃은 별도. 좁은 화면에서는 메뉴 drawer·본문 가로 넘침을 개별 처리.
- Server Component 기본을 유지하고 이벤트·상태·브라우저 라이브러리 경계만 Client Component. 일반 업무 조회는 Query+Axios 단일 경로. Next 서버에서 같은 데이터를 다시 조회·캐시하지 않음.
- 밝은 테마1개, 일반CSS/CSS Modules/CSS변수. 색상/간격/높이/반경/z-index는 styles/tokens.css 원본, 개별 화면이 같은 값을 반복하지 않음. 외부 폰트/CDN 기본없음.
- 메뉴 registry는 id/label/href/icon/permission/children, 제목·현재 위치도 같은 정보 사용. 아이콘은 프로젝트 내 SVG 컴포넌트, 별도 아이콘 패키지 자동추가 없음. 필수 접근성 이름 제공.
- Zustand 실제 사용은 sidebar 접힘/모바일메뉴/예제옵션 중 공유 UI 상태다. session 정보·Query 응답·비밀번호를 중복 저장하지 않음. sidebar 환경설정만 버전 붙인 비민감 localStorage에 저장 가능, 토큰/개인정보/검색값은 금지.
- loading/empty/error/forbidden/not-found/saving을 공통 컴포넌트로 구분, 오류를 빈 결과로 숨기지 않음.

## 2. 설치 라이브러리별 사용 추적표

아래 위치는 예정 책임이며 기존 파일 경로/역할을 우선한다. 완료 시 실제 파일·예제 주소·검사 결과로 치환한다. 초기 상태는 전부 이번 기능 미검증이다.

| ID | 라이브러리 | 필수 실제 사용 | 확인 화면·증거 |
| --- | --- | --- | --- |
| LIB01 | next | App Router·layout·error/not-found·정확한환경build·nonce | 메뉴 이동/새로고침/오류/production 산출물 |
| LIB02 | react, react-dom | 실제 컴포넌트·이벤트·폼·hydration | 카탈로그 전체의 실제 조작 |
| LIB03 | @tanstack/react-query | Orval 조회/변경Hook·로딩·캐시·취소·관련무효화 | API 조회·사용자/부서·인증 전환 |
| LIB04 | axios | 기존 src/api/client.ts의 mutator·쿠키·CSRF·타임아웃·안전오류 | 실제 /api 통신·생성클라이언트 호출 |
| LIB05 | zustand | sidebar/메뉴 또는 공유 예제 설정 | 여러 화면에서 UI 상태 반영·reset |
| LIB06 | react-hook-form | register/Controller·submit/dirty/error/reset | 폼 예제·로그인·비밀번호 변경 |
| LIB07 | zod | 폼 schema·문자열숫자/날짜검증·오류계약 경계 | 정상/형식/범위/서버오류 연결 |
| LIB08 | @hookform/resolvers | zodResolver로 RHF와 실제 연결 | 제출/첫오류/수정 중재검증 |
| LIB09 | radix-ui | Dialog/AlertDialog/Select/Checkbox/RadioGroup/Switch/Tabs/Tooltip/Collapsible/DropdownMenu/Popover의 필요한 Primitives | 컨트롤 예제·키보드·포커스·CSP |
| LIB10 | ag-grid-community, ag-grid-react | 공통 DataGrid, Community 모듈만·날짜/숫자formatter·선택·정렬 | 합성/서버페이징 그리드 두 예제 |
| LIB11 | echarts | 직접 init/setOption/resize/dispose, 막대/선/원형 | 차트 탭·빈데이터·크기변경·정리 |
| LIB12 | decimal.js (추가계획) | 공통 decimal 계산/비교/반올림, 문자열 입력/출력 | 정밀 계산·금액입력·숫자 그리드정렬 |
| LIB13 | orval | 실제 OpenAPI→타입/Axios/Query hook 생성, 생성 결과 화면에서 사용 | npm run api:gen·생성차이·타입/빌드·실제조회 |
| LIB14 | typescript, @types/react, @types/react-dom, @types/node | strict 타입·생성API·컨트롤속성·설정스크립트 | npm run typecheck 통과 |
| LIB15 | eslint, eslint-config-next | 프로젝트lint 실제 적용 | npm run lint 통과 |
| LIB16 | prettier | 코드/스타일/설정 형식 | npm run format:check 통과 |

decimal.js는 문자열 소수 연산 목적의 신규 직접 의존성이다. 설치 전 공식 npm 메타데이터에서 안정 버전/라이선스/engines를 확인해 정확 버전과 package-lock을 갱신하고 버전표에 실제 선택을 기록한다. 이번 문서 작성에서 registry latest 응답을 도구로 읽을 수 없어 정확 최신 버전을 확인했다고 주장하지 않는다. 나머지 기존 버전은 임의 업그레이드하지 않는다. 일반 간접 의존성까지 일부러 import해서 사용률을 채우지 않는다.

## 3. 공통 컨트롤 전체 범위 (프론트2단계)

| ID·예정 컴포넌트 | 필수 동작·옵션 | 예제 |
| --- | --- | --- |
| UI01 Button/IconButton | default/primary/danger/text, small/medium/large, disabled/loading, 앞뒤아이콘, 기본type=button | buttons |
| UI02 TextInput/PasswordInput/Textarea/SearchInput | ref/HTML속성·disabled/readOnly/error/maxLength·지우기, 암호표시전환·비밀번호관리자 | inputs |
| UI03 NumberInput/AmountInput | 값string, 음수/자릿수/범위·천단위표시, 편집중 -/0. 보존, API값구분 | numbers |
| UI04 DateInput/DateTimeInput/DateRangeInput | 날짜/초/밀리초3형식·strict검증·min/max/clear·시작종료검증, 일시는UTC↔표시시간대 | dates |
| UI05 Checkbox/CheckboxGroup/RadioGroup/Switch | controlled값·disabled·키보드·label, radio단일/checkbox복수 | selections |
| UI06 Select/Combobox/MultiSelect | value/label/disabled 공통, 단일/검색/복수·clear·빈결과·로딩 | selections |
| UI07 FormField/FormError/FormActions | label/required/help/error·aria연결·가로/세로배치, RHF연결과독립사용 | forms |
| UI08 Dialog/ConfirmDialog/SidePanel | title/size/footer·닫기허용·focus복귀·Escape·취소·진행중닫기정책 | overlays |
| UI09 Toast/InlineAlert | 성공/안내/경고/오류·dismiss·적절aria-live, 중복알림방지 | feedback |
| UI10 Tabs/Tooltip/Collapsible/ActionMenu | 키보드/포커스·활성/비활성·제어상태·메뉴선택 | navigation |
| UI11 Badge/Spinner/Skeleton/EmptyState/ErrorState | 색만으로의미전달금지·재시도·화면/버튼로딩구분 | feedback |
| UI12 Pagination/SearchPanel | page0↔표시1, size20/max100, 총건수·조회/초기화/접힘/Enter·적용조건분리 | search |
| UI13 DataGrid | 아래3.1·선택/정렬/formatter/페이지/상태·안정rowId | grid |
| UI14 Chart/BarChart/LineChart/PieChart | 아래3.2·색/범례/tooltip/resize/empty/error | charts |

컨트롤 자체는 API 호출/업무권한 결정을 소유하지 않는다. 기본disabled/readOnly 의미를 구분하며 TextInput의 trim/HTML치환·NumberInput의Number변환을 자동 적용하지 않는다. Radix에 없는 기능은 React+HTML+기존 Primitives로 구현하되 새 대형 UI/날짜 라이브러리는 필요 근거 없이 추가하지 않음. 날짜팝업은 필요 최소 범위의 달력 또는 native picker를 사용하더라도 브라우저 표시가 계약형식과 다를 수 있으므로 canonical 문자열 입력/표시를 보장한다. HTML date/datetime-local만 붙이고 완료하지 않는다.

### 3.1 그리드

클라이언트 전체데이터 모드와 서버페이징 모드를 명시적으로 구분한다. Community client row model에 서버가 반환한 한 페이지를 제공하고 공통 Pagination/정렬 이벤트를 외부 Query 조건에 연결한다. Enterprise Server-Side Row Model·Set Filter·Excel/pivot/그룹을 자동 도입하지 않음. 서버 모드에서 한 페이지 내부 client정렬/필터를 전체결과인 것처럼 적용하지 않는다. sort는 백엔드 허용 enum만.

columns/rows/loading/error/rowKey/selection/onSortChange/onPageChange를 공통 계약으로 제공하되 AG Grid 전체 API를 자체 재작성하지 않는다. renderer는 React노드/검토된함수만, 사용자 HTML·문자열expression 금지. 날짜/금액 표시는 공통 formatter, 정밀수치 정렬은Decimal비교, ID는string. 선택상태는현재 페이지/명시rowID 기준이며 서버전체선택으로 오인하지 않음. 조회오류·빈목록·로딩을 구분하고 rowaction 키보드 동작 포함.

셀편집/대량저장은 이번 범위 밖이다. 사용자 요청의 '누락 금지'를 이유로 AG Grid 유료/미합의 전체 기능을 범위에 추가하지 않으며, 이 제외와 추가조건은 예제설명에도 명시한다.

### 3.2 차트와 CSP

ECharts는직접 wrapper,별도 React wrapper 패키지없음. canvas renderer/richText tooltip을기본,임의HTMLformatter/외부URL제외. 선택한 chart/renderer/component만등록. ResizeObserver·중복init방지·unmount dispose·0크기/hidden tab복원/empty/error를처리한다. 금액 계산은Decimal 원본으로하고 차트 좌표number 변환은유한범위·표현정밀도 제한을명시한다. 원문 정확한 금액은 별도 접근 가능한 표/라벨로 제공.

기존HDR07 유지: AG Grid styleNonce에 요청nonce 전달, 외부Google폰트 금지·내장아이콘data이미지는img-src한정, 문자열평가금지. Radix의portal/position/scrolllock·ECharts DOM/CSSOM 경로를production에서확인. unsafe-inline/unsafe-eval 전역추가·CSP삭제·라이브러리사용제거로통과처리하지않음. 문제가있으면원인/대안/최소변경을보고하고설계조정.

## 4. 기능·예제 메뉴 매핑 (프론트3~4단계)

| ID·경로 | 필수 내용 | 유형·권한 |
| --- | --- | --- |
| EX01 / | 기능목차·완료상태·링크·실행안내 | 일반 시작화면 |
| EX02 /examples/buttons, inputs, selections, dates, overlays, feedback, navigation, search | UI01~UI12 각각실행·기본/오류/disabled/readOnly/loading·주요옵션·짧은코드 | local예제 |
| EX03 /examples/forms | RHF+Zod+resolver·직접입력/Controller·첫오류·수정재검증·dirty/이탈·reset·제출중중복방지·값유지 | 합성폼, 실제저장아님표시 |
| EX04 /examples/grid | 합성컬럼/선택/표시·클라이언트정렬, 실제서버페이지예제연결 | 합성 + API연동구분 |
| EX05 /examples/charts | 막대/선/원형·크기·빈데이터·로딩·오류·접근가능표 | 합성데이터 |
| EX06 /examples/numbers | 0.1+0.2·단가×수량·행합계·할인/나눗셈·반올림·큰ID·표시/전송JSON비교 | Decimal실제계산 |
| EX07 /examples/api | 생성API호출·Query캐시/로딩/실패/취소·유효성오류·미등록 code의 안전한 fallback·traceId | 실제호출+합성오류표시구분 |
| EX08 /examples/auth | 로그인/갱신/로그아웃/권한표시·보호버튼·다중탭·동시정책확인안내 | 실제기능연결·읽기표시 |
| EX09 /login | RHF+Zod·CSRF·제출/실패·공통메시지 | 실제API |
| EX10 /me | 내정보·비밀번호변경·현재/전체로그아웃 | 인증필수 |
| EX11 /users, /users/[userId] | 생성API·서버페이징/정렬·프로필·API권한거부·목록복귀조건유지 | USER_READ/상세PERSONAL_INFO_READ |
| EX12 /departments | 부서목록·검색/페이지·공통그리드 | DEPARTMENT_READ |
| EX13 /audits/access, /audits/security | 기간조건·이력목록·권한·IP축약·공통날짜/페이지 | AUDIT_READ/SECURITY_AUDIT_READ |

EX02 경로는 /examples/{각slug}로 전부 제공, EX04 서버예제는 /users 기능을 공통 재사용한다. 경로 registry와 제목·메뉴는한원본. 목록복귀조건은 탭메모리에유지하고 민감검색어를URL/localStorage에기본저장하지않음.

예제는 APP_EXAMPLES_ENABLED의 빌드입력 local=true/shared=false, 공개가능boolean만정적반영, 비활성에서메뉴만숨기지말고직접URL도404. 빌드입력추가시기존4환경체계·공통npm start유지, dev를local의별칭으로만들지않음. 예제코드의비밀보호를플래그에의존하지않으며 합성데이터는공개가능한것만. 일반 /me·사용자/부서/이력기능은권한으로보호하고예제끄기와별도다.

각 예제에는 동작 설명·공통 컴포넌트 import경로·주요속성·안전한 사용코드를 표시한다. React가 escape하는 pre/code와클립보드복사(권한실패안내) 사용, 임의HTML/markdown실행/문법강조패키지미추가. 샘플은공통구현을직접사용하고중복판박이예제를만들지않는다.

## 5. 인증·API·상태·폼 세부 계약

- backend-handoff의 API/payload/HEAD me/쿠키/오류가원본. Redis/JDBC를프론트가선택하지않음. 현재1차의'모든401즉시캐시초기화'는TOKEN_EXPIRED복구와충돌하므로기존경계에서수정한다.
- `npm run api:gen`은 백엔드가 생성해 저장소에 반영한 `docs/api/openapi.json`을 기본 입력으로 사용한다. 다른 로컬 명세를 사용하려면 `OPENAPI_SPEC_PATH`를 선택적으로 지정한다. 명시한 경로가 잘못됐을 때 기본 명세로 조용히 대체하지 않는다. 생성된 타입과 함수는 API 예제·사용자·부서·이력 화면에서 직접 사용한다. 가짜 명세나 빈 생성 파일로 연동을 완료했다고 표시하지 않는다. 실제 명세가 준비되지 않았으면 화면과 순수 기능 작업은 계속하고 API 연결만 대기 상태로 남긴다.
- Query에현재사용자, Zustand에UI만. Form은RHF, 로컬은useState. QueryKey는검색/페이지/정렬/인증세대포함, 자동변경재시도없음. 저장성공관련무효화/실패입력유지. me의포커스/interval자동재조회off로idle연장방지.
- 로그인상태 checking/authenticated/anonymous/error구분. 초기csrf→HEAD me→필요시refresh1회→GET me. auth를필요로하지않는합성카탈로그에서주기적으로me를호출하지않음. 토큰/refresh/비밀번호·사용자응답을영속화하지않음.
- 갱신은탭내single-flight·탭간Web Locks,lock후HEAD me확인,로그인/로그아웃/사용자전환도같은auth변경잠금으로경쟁을직렬화. BroadcastChannel에는상태/세대알림만. 잠금미지원은자동갱신off·재로그인. 응답유실·재사용401은재로그인,단순503은로그인실패와구분.
- GET/HEAD의허용인증복구후재시도1회, unsafe자동재전송기본off. CSRF는메모리·공통Axios헤더,login/logout후재조회,여러탭의CSRF교체는발급endpoint재조회로해결하고변경자동재전송하지않음. 같은오류필드/폼/toast중복안내금지.
- 권한usePermission/PermissionGuard는Query user.permissions사용,직접URL보호·메뉴/버튼숨김기본,필요비활성선택. 서버최종권한은우회불가. 비밀번호폼은current/new/confirm,confirm은프론트검증만이며API로보내지않음. 서버code/field만안전메시지로변환.
- UTC3형식 엄격파싱·날짜만변환없음. 표시시간대는공통Asia/Seoul기본으로명시(화면에시간대표시), API/로그/DB UTC 유지. 브라우저new Date('공백형식')의암묵파싱금지.
- Decimal은전용clone에서precision40·ROUND_HALF_UP,예제 입력은 최대18 유효자리·소수6자리, 합계 최대1000행으로 제한하여 곱셈과 합계의 유효자리가40을 넘지 않게 하고 결과를 검증한다. 나눗셈은40자리 정밀도의 근삿값에서 명시 반올림하며, 더 큰 업무 범위는 DB p/s와 연산 최대폭을 기준으로 별도 설정한다. 금액예제는소수2자리최종반올림,계산중각항반올림/최종합계반올림차이도표시. 0제수·NaN/Infinity/지수입력은검증오류. ID문자열유지, Number/parseFloat를중간변환으로쓰지않음. 일반page/size는number유지.
- 정밀계산은프론트실제예제를먼저완성,백엔드계산전용API는현재계약에없으므로발명하지않음. 동일입력JavaBigDecimal/Decimal기대값fixture로비교가능,실제API왕복예제가필요하면설계담당이별도계약추가. UI의JSON미리보기만으로실제HTTP왕복검증통과주장금지.
- logout/user전환시inflight조회취소→캐시/사용자/폼정리→다른탭알림,이전인증세대응답을폐기해cache재주입방지. network오류인logout은서버폐기미확인을안내하고성공이라표시하지않음.

## 6. 구현·완료 추적과 검증

frontend/README에별도표 또는frontend/기능완료표를두고LIB01~16/UI01~14/EX01~13각ID에실제파일·메뉴·검증·상태를기록한다. 상태는 planned/implemented/verified/dependency-pending/excluded(명시범위밖). 모든포함ID는verified가되어야전체완료,미사용직접의존성·설정만완료·빈페이지·TODO·클릭해도미동작은허용하지않음. backend가없어서못한항목은누락이아니라미완료로공개한다.

| ID | 필수확인 |
| --- | --- |
| FV01 | npm기존버전·단일lock·타입/lint/format/local·prod build,4환경선택/공통start회귀 |
| FV02 | package.json직접의존성전부LIB표대조,실제import/실행·생성클라이언트소비·사용없는의존성0 |
| FV03 | UI01~14전옵션기본/비활성/읽기전용/오류/로딩,클릭·키보드·모달focus·긴문구/모바일 |
| FV04 | EX01~13메뉴/직접URL/새로고침/권한,카탈로그off직접404·실데이터노출없음 |
| FV05 | 그리드client/server분리·안정행ID·허용sort·페이지상한·숫자정밀정렬·선택상태 |
| FV06 | ECharts3종·resize/hidden-tab/unmount·중복인스턴스없음·대체표/라벨 |
| FV07 | RHF/Zod/resolver·서버field연결·중복제출·dirty/이탈·실패값유지·메시지fallback |
| FV08 | 실제backend/OpenAPI/Orval·조회/로그인/refresh/logout/password·오류·이력읽기 |
| FV09 | 다중탭갱신/로그아웃·동시로그인3정책·권한없음·캐시격리·401/403/409/429/503분기 |
| FV10 | 큰정수·0.1+0.2·합계/반올림/0제수·40자리한계·표시/전송구분·UTC왕복 |
| FV11 | production CSP·nonce/Grid/Radix/tooltip·XSS/정제오류·개인정보/토큰/내부주소미노출 |

기본은기존typecheck/lint/format/build와브라우저수동확인. 순수숫자/날짜검사는기존Node실행기능으로필요한회귀를확인하고Vitest/RTL/Playwright/Storybook을자동설치하지않음. 브라우저실행/연결을못했으면정확히미검증. 모든의존성을사용한다고모든기능검증통과라표현하지않는다.

공개문서에는실제비밀/개인경로/coordination로그금지. 임의DB쓰기/새API/seed·컨테이너/배포·커밋/push는프론트구현범위에포함하지않음. 실제로그인·비밀번호변경시험도대상·영향·복구를명시한승인범위에서수행한다. code-only구현과승인필요실행을분리한다.

## 7. 자료와 현재 상태

- [decimal.js API](https://mikemcl.github.io/decimal.js/): 문자열소수·clone/precision·반올림 API확인.
- [AG Grid 보안](https://www.ag-grid.com/react-data-grid/security/), [ECharts renderer](https://echarts.apache.org/handbook/en/best-practices/canvas-vs-svg/): 기존HDR07과함께구현시적용. 문서열람이선정버전의CSP실행통과를뜻하지않음.
- 위URL관련절은2026-10-29열람,전체브라우저실행/패키지설치/Orval생성은이번에하지않음.

이번에는설계와누락방지추적표를작성했다. 프론트작업자구현지시는아직보내지않았으며백엔드구현은별도진행중이다. 최종구현검토는공통코드·예제·실제의존성사용·증거를대조해판정한다.
