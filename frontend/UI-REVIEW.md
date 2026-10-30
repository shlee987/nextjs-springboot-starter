# 공통 UI 정리와 화면 점검

2026-10-30 파일 API 연결·내 파일 목록/다운로드/삭제·SECFILE11 디자인 및 날짜 외부값 부모 오류 동기화의 마감 기록은 [SECFILE.md](SECFILE.md)·[UI-OPTIONS.md](UI-OPTIONS.md)를 참조합니다. 아래 화면·생성36파일·기능54개 상태 표현은 각 검수 시점의 이력이며 현재 생성물은43파일입니다.

2026-10-30 UIOPT01~09·라디오 안쪽 원12px·작업별 실행 잠금의 후속 구현과 증거는 [공통 컨트롤 보완표](UI-OPTIONS.md)에 기록합니다. 아래 기존 UIREF01~19와 버튼 정렬 검증 이력은 보존합니다.

2026-10-29 작업입니다. 화면의 정렬·간격·글꼴과 공통 컨트롤을 정리했습니다. 기존 기능 54개 중 42개 verified·12개 부분 완료 이력은 [FEATURES.md](FEATURES.md)에 그대로 남깁니다. 이번 스타일 변경만으로 미검증 기능을 완료로 바꾸지 않습니다.

## 바뀐 화면

- 목차와 메뉴를 입력과 선택, 피드백과 탐색, 데이터 표현, 실서비스 연결의 네 분류로 묶었습니다. 카드마다 용도를 한 문장으로 설명합니다. 서버 권한이 필요한 항목은 같은 메뉴 원본에서 걸러냅니다.
- 입력과 버튼의 높이·행간을 맞췄습니다. 입력 옆 지우기·표시 버튼은 한 글자씩 줄바꿈되지 않습니다. 기본 select로 남아 있던 정렬과 페이지 크기는 공통 Select로 바꿨습니다.
- 내 정보는 기본 정보·권한·로그인 관리·비밀번호 변경으로 나눴습니다. 이름·아이디·부서를 두 번 보여주던 부분을 없애고 긴 권한은 줄바꿈되는 배지로 표시합니다.
- 예제는 용도 → 해볼 동작 → 예상 결과 → 컨트롤과 결과 → 접힌 사용 코드 순서입니다. 기술 이름은 필요한 보조 설명과 코드에 남깁니다. 화면에서만 동작하는 예제와 실제 서버 요청을 구분합니다.

## UIREF01~14 확인표

| ID      | 구현 위치                                                                           | 실제 확인 범위                                                                          | 상태           |
| ------- | ----------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- | -------------- |
| UIREF01 | `public/fonts/pretendard/*`, `app/globals.css`, `styles/tokens.css`, `DataGrid.tsx` | 공식 v1.3.9 WOFF2 자체 제공·OFL 동봉, HTTP200/wOF2·실제 Pretendard 로드                 | 확인           |
| UIREF02 | `tokens.css`, `globals.css`, `ui.module.css`                                        | 본문15/모바일16·표14·제목26·행간, 320~1920 폭. 실제 브라우저 200% 확대는 아래 한계 참조 | 일부 확인      |
| UIREF03 | `tokens.css`, `ui.module.css`                                                       | 본문·보조 문자·주요 버튼·입력 경계 대비, hover/focus/disabled/loading                   | 확인           |
| UIREF04 | `ui.module.css`, `AppShell.module.css`                                              | 4px 간격 스케일, 같은 행 PC 입력40·모바일44, 좁은 입력 그룹                             | 확인           |
| UIREF05 | `Icon.tsx`, `Selections.tsx`, `Navigation.tsx`, `Feedback.tsx`, `Overlays.tsx`      | 체크·라디오·화살표·닫기를 SVG/CSS로 표시, 접근 이름 유지                                | 확인           |
| UIREF06 | `AppShell.tsx`, `AppShell.module.css`, `Overlays.tsx`                               | 992px sidebar/drawer 전환, 실제 가용 폭, 메뉴 Escape·focus·내부 스크롤                  | 후속 최종 확인 |
| UIREF07 | `ui.module.css`, `AppShell.module.css`                                              | 22개 화면 1440/390, 대표6화면×12폭·경계 전후, 전체 문서 가로 넘침0                      | 확인           |
| UIREF08 | `DataGrid.tsx`, `Overlays.tsx`, `Feedback.tsx`, `ui.module.css`                     | Grid 내부 가로 스크롤, 팝업 여백/내부 스크롤·상단 닫기, Toast 여백                      | 후속 최종 확인 |
| UIREF09 | `Selections.tsx`, `Search.tsx`, `DataGrid.tsx`, `FormsExample.tsx`, 오류 페이지     | 화면 직접 select/input/button 제거, 공통 Select 정렬/size·RHF ref/onBlur·키보드 유지    | 확인           |
| UIREF10 | `Inputs.tsx`, `Selections.tsx`, `ui.module.css`, `Form.tsx`                         | input flex/min-width·부속 버튼·fieldset/legend reset·옵션/목록 footer                   | 확인           |
| UIREF11 | `Feedback.tsx`, `ui.module.css`, `ControlExamples.tsx`                              | 상태 종류·본문·우상단 X·별도 재시도, 긴 알림·중복·자동 제거                             | 후속 최종 확인 |
| UIREF12 | `Search.tsx`, `DataGrid.tsx`, `ProfilePanel.tsx`, `AuthScreens.tsx`, 데이터 화면    | 검색→결과 도구→Grid→페이지, 실제 목록/프로필·권한 배지·중복 정보 제거                   | 확인           |
| UIREF13 | `config/navigation.ts`, `app/page.tsx`, `AppShell.tsx`                              | 동일 원본 네 분류·고유 용도·권한·예제 off 직접404                                       | 확인           |
| UIREF14 | `ExampleSection.tsx`, 예제5개 파일, `messages.ts`, `README.md`                      | 목적/동작/결과·상태 비교·접힌 코드·쉬운 문장, 실제 요청/화면 예시 구분                  | 확인           |

## 직접 컨트롤 사용처 점검

`src`의 모든 TSX를 검색했습니다. 직접 `<select>`는 없습니다. 직접 `<input>`은 공통 `Inputs.tsx`, 직접 `<button>`은 공통 `Button.tsx`와 `Selections.tsx`의 검색 목록 option에만 있습니다. option은 listbox의 선택 항목이므로 일반 동작 버튼과 다른 의미를 유지합니다. 화면의 제출 실패 체크박스와 오류 페이지 버튼도 공통 구현을 사용합니다.

RHF 폼 안에서 Radix가 만드는 숨은 native select는 접근/폼 이벤트를 위한 내부 구현입니다. 화면에 기본 HTML select가 노출된 사례와 구분합니다. Radix의 label·ref·onBlur·onChange와 페이지0/표시1·크기 상한100·허용 정렬은 유지했습니다.

## 실제 검수

- 원본 13000에서 목차·입력·선택·폼·목록·프로필·알림의 PC/모바일 전 화면14개를 저장했습니다. 변경 후에는 모든 메뉴와 로그인·사용자 상세를 포함한 22개 화면을 1440×1000, 390×844에서 저장했습니다. 입력한 비밀번호·토큰은 이미지에 담지 않았습니다.
- 실제 CSS viewport를 매번 확인했습니다. 320/360/575/576/767/768/991/992/1024/1199/1200/1920의 12폭에서 대표6화면을 확인한 72개 측정은 문서 가로 넘침0입니다. Grid의 의도된 내부 스크롤은 그대로 유지합니다. PC 입력 높이40px·모바일44px을 DOM으로 확인했습니다.
- 브라우저 도구의 viewport 기능은 선택된 탭에 적용됩니다. 초기에 회귀 탭이 선택된 상태에서 폭이 잘못 적용된 결과는 폐기하고, 검사 탭 하나만 남긴 뒤 실제 innerWidth가 요청값과 같은지 단언하며 전체 캡처와 측정을 다시 수행했습니다.
- HTTP 독립 검사: local 14예제200 / prod 14예제404, 공유 목차 예제 링크0, strict CSP/매 요청 nonce/no-store/SSR 인라인 style0, 자체 WOFF2 HTTP200과 파일 magic이 통과했습니다. API·쿠키 정책을 완화하지 않았습니다.
- 흰 배경 대비: 본문15.96:1, 보조문자6.29:1, 주요 버튼6.43:1, 위험 버튼6.19:1, 입력 경계3.04:1입니다. 비활성 표시를 정상 본문 대비로 세지 않았습니다.
- 자체 글꼴은 Pretendard v1.3.9 원본 Variable WOFF2(2,057,688 bytes)입니다. SHA-256은 `9599F12FD42FC0BCE1CD50B47A0C022E108D7AA64DD0D1BB0ED44F3282D900B4`입니다. CSS는 굵기400~700·swap·자체 URL만 지정합니다. 공식 출처와 OFL1.1 원문은 자산 폴더에 보관했습니다.
- 기존 숫자/날짜/환경 회귀9개와 실제 Provider 기반 인증 회귀17개가 통과했습니다. 두 회귀는 실제 서버 비밀번호 변경이나 B5 native 이탈 확인을 대신하지 않습니다.

전후 원본 이미지·전체 화면 갤러리·측정 JSON·실행 로그는 공개 저장소 밖 작업공간에 보관합니다. 이미지 파일명은 `before/{pc,mobile}-{home,inputs,selections,forms,list,profile,feedback}.jpg`, `after/{pc,mobile}-{화면}.jpg`입니다. 실행 PC의 절대 경로는 이 공개 문서에 넣지 않습니다.

## 문장을 쉽게 바꾼 예

| 이전                               | 현재                                                                        |
| ---------------------------------- | --------------------------------------------------------------------------- |
| RHF + Zod 폼                       | 입력 검증과 제출 테스트                                                     |
| 합성 제출 / 합성 제출 실패         | 제출 테스트 / 제출 실패 재현                                                |
| 서버 저장 아님                     | 이 예제는 서버에 저장하지 않습니다.                                         |
| 통신 검증은 준비·승인이 필요합니다 | 실제 서버의 내 프로필을 읽습니다. 먼저 로그인하거나 기존 세션을 확인하세요. |
| 갱신 경계 확인                     | 로그인 유지 확인                                                            |
| 인증 세대·영문 상태 나열           | 로그인 상태: 확인 전 / 로그인됨 / 로그인 필요 / 확인 실패                   |

개발자용 API·환경변수·오류 코드·과거 검증 증거의 의미는 바꾸지 않았습니다. 이번 안내는 README·FEATURES의 연결 설명과 변경 이력에도 반영했습니다.

## 실행과 한계

기존 사용자 3000·13000 프로세스와 산출물은 유지했습니다. 독립 검증본은 local13010, prod13011에서 확인합니다. 13010은 기존 backend trusted origin에 추가하지 않았으므로 실제 인증 변경 요청을 수행하지 않습니다. 기존13000에서 승인된 테스트 계정을 확인한 뒤 같은 localhost 쿠키로 보호 GET을 검수했습니다. 만료 때문에13010에서 Origin 거부가 나면13000에서 세션을 갱신하고 조회를 다시 확인했습니다. 이 제약을 제품 오류나 인증 성공으로 바꾸어 기록하지 않습니다.

내장 브라우저는 확대 단축키를 보내도 innerWidth·devicePixelRatio가 변하지 않았고, 제공된 API에 zoom 기능이 없습니다. 실제200% 확대는 미검증입니다. 1440의200%와 같은 CSS 가용 폭720에서 재배치를 따로 확인하되 실제 zoom 성공이라고 주장하지 않습니다. 모바일 회전도 viewport의 가로/세로 전환으로 확인한 범위만 기록합니다.

B5의 native 이탈 대화상자 처리 timeout은 반복하지 않았습니다. 비밀번호·권한·DB/Redis 정책·migration·컨테이너는 변경하지 않았고 커밋/push도 하지 않았습니다. 독립 리뷰는 설계 담당이 별도로 진행합니다.

## 2026-10-30 추가 수정 — UIREF15~19

아래 결과는 사용자 재점검 후 추가 수정입니다. 위 UIREF01~14와 기존 54개 기능의 상태·과거 수치는 그대로 보존합니다. 이전 색상 대비는 2026-10-29의 기록이며 현재 색상은 아래 표를 따릅니다.

| ID      | 구현 위치 (`src/` 기준)                                                                                                             | 실제 확인                                                                                                                          | 상태 |
| ------- | ----------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- | ---- |
| UIREF15 | `styles/tokens.css`, `components/ui/ui.module.css`                                                                                  | 청 #2563eb·적 #e11d48·녹 #008744, hover/pressed/soft·알림·배지·링크·체크·달력 선택 표시, PC/모바일 색상 캡처                       | 확인 |
| UIREF16 | `components/ui/Feedback.tsx`, `Button.tsx`, `ui.module.css`, `features/examples/ControlExamples.tsx`                                | 영역·인라인·목록·버튼 로딩의 gap8px, 아이콘 shrink0, 진행 전후 버튼 폭 동일                                                        | 확인 |
| UIREF17 | `components/ui/Selections.tsx`, `ui.module.css`                                                                                     | 체크박스 배경 상속 제거, 흰 원20px·중앙 점10px, 미선택/선택/비활성/포커스·방향키로 선택 및 비활성 건너뛰기                         | 확인 |
| UIREF18 | `components/ui/Selections.tsx`, `ui.module.css`                                                                                     | 입력과 결과 gap12px, 별도 빈 영역·footer, 0/1/30개·긴 항목 검색/선택·키보드, 320/360/390/1440 팝업 좌우16px                        | 확인 |
| UIREF19 | `components/ui/DatePickers.tsx`, `Inputs.tsx`, `Icon.tsx`, `ui.module.css`, `lib/dates.ts`, `features/examples/ControlExamples.tsx` | 날짜/날짜시간/기간의 실제 달력, 직접 편집·월/연도·오늘·선택·지우기·확정/취소·키보드·ref·UTC/서울·범위·극단 연도와 모바일 열린 모습 | 확인 |

### 공통 달력의 값과 동작

- `DatePicker`·`DateTimePicker`가 같은 달력을 사용합니다. `Inputs.tsx`는 기존 DateInput·DateTimeInput·DateRangeInput 이름도 내보내므로 이력 기간 검색 등 기존 호출부가 같은 선택기를 사용합니다. 공개 문자열 값과 ref/onBlur/label/disabled/readOnly/min/max 계약을 유지했습니다.
- 날짜만 값은 시간대 변환을 하지 않습니다. 날짜시간은 기존 엄격한 `fromDisplayDate`/`toDisplayDate`로 UTC와 명시한 Asia/Seoul 또는 UTC를 왕복합니다. 날짜를 바꿔도 시·분·초·밀리초를 보존합니다. 팝업에서 바꾼 값과 지우기는 확인으로 반영하며 취소·Escape·외부 닫기는 확정하지 않습니다. 직접 입력의 지우기는 즉시 반영합니다.
- 방향키로 일/주, Home/End로 주 경계, PageUp/PageDown으로 월을 이동합니다. Enter/Space로 날짜를 선택하고 Escape 뒤 열기 버튼으로 포커스가 돌아옵니다. 월말 이동은 새 월 말일로 제한하며 0001·9999년을 넘지 않습니다. 오늘은 테두리·밑줄, 선택 날짜는 채운 배경과 접근 가능한 이름으로 구분합니다.
- 직접 입력의 범위 밖 날짜·없는 날짜·서울 DST 누락 시각은 오류로 남고 확정값을 바꾸지 않았습니다. 기간 역전/31일 초과는 기존 공통 검사와 검색 제출 차단을 유지합니다. UTC 최대 연도를 서울로 표시해 10000년이 되는 상한은 달력 밖의 상한으로 처리하며, 확정 시에는 원래 UTC 한계를 다시 검사합니다.
- 팝업 폭은 Radix가 계산한 실제 가용 폭을 사용합니다. 스크롤바가 있는 모바일에서도 좌우16px을 확보했습니다. 날짜시간의 본문은 내부 스크롤하고 확인/취소는 하단에 남습니다. 320/360/390/1440에서 열린 팝업과 확인 버튼이 화면 안에 있음을 실측했습니다.

### 실행 결과와 시각 증거

- 최종 local/prod webpack 빌드, typecheck, lint, 변경 파일 Prettier·diff 공백 검사가 통과했습니다. 기존 날짜·정밀 숫자·환경 검사에 달력 월말/윤년/0001~9999 경계 그룹을 더한 foundation10개가 통과했습니다. 실제 Provider 기반 기존 인증 회귀17개도 PASS이며 외부 API/DB 요청은 없습니다.
- local 예제14개200 / prod 예제14개404, shared 목차의 예제 링크0, strict CSP·nonce 변화·no-store·SSR 인라인 style0·자체 글꼴 HTTP200을 재확인했습니다. 최종 production 화면에서 캡처된 경고/오류는 0개입니다. 편집 중 3000 HMR의 순간적인 파일 연결 경고와 최종 production 결과를 구분했습니다.
- 흰 배경 대비는 청5.17:1·적4.70:1·녹4.62:1입니다. hover/pressed도 각각 청6.70/8.72, 적6.29/8.02, 녹5.74/7.42로 확인했습니다. 알림은 문구·아이콘·톤을 함께 사용합니다.
- 버튼·선택·날짜·알림·폼·그리드6개 화면 × 320/360/390/768/1024/1440/1920 = 42개 뷰에서 실제 viewport를 확인했고 문서 가로 넘침은 0개입니다. 그리드의 내부 가로 스크롤은 유지합니다.
- 기존 React/RHF/esbuild로 만든 저장소 밖 임시 검사 화면에서 필수 오류 시 DatePicker ref로 포커스 복귀, 날짜 제출, UTC 시각 유지, 30개 검색 항목·긴 이름 1개 선택을 확인했습니다. `loading={false}`→true일 때 같은 버튼 폭은 110.40625px로 유지됐습니다. 새 테스트 플랫폼·의존성은 추가하지 않았습니다.
- 브라우저 locator의 순간적인 keydown/up은 Radix의 비동기 포커스보다 먼저 종료될 수 있어 라디오 선택 증거로 쓰지 않았습니다. 실제 키 누름100ms로 방향키 선택·비활성 건너뛰기를 확인한 이미지/측정을 최종 증거로 남겼습니다.

전후 이미지와 측정·로그는 공개 저장소 밖 `ui-additions` 작업 폴더에 보관합니다. 이번 수정 전 선택/검색 빈 결과는 2026-10-30 직접 캡처했고, 이전 알림·날짜 화면은 2026-10-29에 저장한 원본을 비교용으로 보존했습니다.

- `before/1440-selections.jpg`, `390-selections.jpg`, `1440-combobox-empty.jpg`, `390-combobox-empty.jpg`
- `after/1440-feedback-colors.jpg`, `390-feedback-colors.jpg`: 청·적·녹색과 영역/인라인 로딩
- `after/1440-radio-focus.jpg`, `390-radio-focus.jpg`, `1440-radio-disabled.jpg`, `390-radio-disabled.jpg`
- `after/{320,360,390,1440}-combobox-empty.jpg`: 입력 외곽 포커스·빈 결과·footer
- `after/{320,360,390,1440}-date-open.jpg`, `after/{320,360,390,1440}-datetime-open.jpg`
- `measurements.json`, `popup-evidence.json`, `calendar-evidence.json`, `calendar-actions.json`, `calendar-max-boundary.json`, `control-fixture-result.json`, `auth-result.json`, `http-result.json`

### 이번 수정 파일과 실행 경계

제품 코드는 tokens.css, ui.module.css, Button.tsx, Feedback.tsx, Selections.tsx, Icon.tsx, Inputs.tsx, 새 DatePickers.tsx, lib/dates.ts, ControlExamples.tsx입니다. 기존 `scripts/verify-foundation.mjs`에 달력 회귀를 추가했고 README·FEATURES·CHANGELOG·이 점검표를 갱신했습니다. API 생성 설정과 생성36개 파일, 의존성·잠금 파일은 이번 추가 수정으로 바꾸지 않았습니다.

착수 시 3000 포트만 실행 중임을 읽기 전용으로 확인했습니다. 그 프로세스와 기존 13000/13010/13011 사본을 종료하거나 덮어쓰지 않았습니다. 새 사본은 local13020/prod13021에서 실행하며 인증 변경 요청에는 사용하지 않습니다. 소스 변경은 기존 3000 HMR에도 표시되고, 이미 열린 화면은 새로고침하면 됩니다. production 반영은 검증된 소스로 기존 빌드 절차를 따른 뒤 조정자가 서버 소유 상태를 확인해 진행합니다.

기존 실제 200% 확대·native 이탈 확인 도구 한계와 기능54개 중 미완료12개의 상태는 유지합니다. 이번 실화면 검증은 독립 리뷰나 운영 검증을 대체하지 않습니다. 독립 리뷰 요청은 조정자가 진행합니다.

## UIREF16 후속 — 제출 버튼 문구 중앙 정렬

사용자가 제출 테스트 버튼의 정렬을 다시 지적했습니다. 앞선 검사에서는 진행 전후 폭이 같은지만 확인해, `loading={false}`에도 빈20px 슬롯과8px gap이 남아 문구를 오른쪽으로 미는 결함을 놓쳤습니다. 위 110.40625px 기록은 그 당시 폭 유지 검사이며 문구 중앙 정렬의 통과 증거가 아닙니다.

- `Button.tsx`·`ui.module.css`를 수정했습니다. 실제 아이콘이 없는 버튼은 빈 슬롯 없이 문구만 중앙에 둡니다. 진행 중에는 문구의 배치 공간을 유지하고 opacity0으로 표시만 숨기며 중앙에 스피너를 겹칩니다. 접근 가능한 버튼 이름·aria-busy·중복 클릭 차단은 유지합니다. 스피너가 나타나도 버튼 폭이 변하지 않습니다.
- 앞/뒤 아이콘이 있으면 해당20px 슬롯을 유지합니다. 진행 중에는 앞 아이콘을 스피너로 바꾸고, 뒤 아이콘만 있는 경우 뒤 슬롯을 바꿉니다. 실제 아이콘과 문구 사이의8px gap과 아이콘을 포함한 기존 배치를 유지합니다.
- 모든 loading 사용처를 검색했습니다. 버튼 소유자는 `Form.tsx`의 FormActions(제출 테스트·로그인·비밀번호 변경), `Search.tsx`의 조회, `Overlays.tsx`의 확인, 버튼 예제입니다. DataGrid·선택 컴포넌트의 loading은 별도 표시이며 이번 버튼 분기로 바꾸지 않습니다.
- PC1440/모바일390의 실제 폼·로그인·확인 버튼은 문구 행 상자와 버튼 상자의 가로 중심 차이가0.001px 미만입니다. 기존 제출 버튼의 오른쪽 치우침은 스크린샷 흰 글자 픽셀 영역으로 약14.9px였습니다. 수정 후 실제 제출 버튼 폭은 PC97.4375px·모바일106.5500px이며 제출 전/진행 중에 같습니다. 확인 팝업도 진행 전/중 폭이 같습니다.
- 저장소 밖 기존 React/esbuild 검사 화면에서 loading 미지정/false/true를 같은 문구로 비교했습니다. 세 상태의 폭이 모두 같고 문구 행 상자의 중심 차이가0.001px 미만입니다. 진행 표시의 opacity0 상태에서도 접근 가능한 이름으로 버튼을 찾았습니다. 실제 아이콘의 앞/뒤/양쪽 조합도 진행 전후 폭이 같습니다.
- 실제 로그인은 기본 상태를 확인했으며 인증 요청·비밀번호 입력/변경은 하지 않았습니다. 로그인과 비밀번호 변경의 진행 표시는 같은 FormActions의 전환 검증으로 구분합니다. 실제 폼의500ms 제출과 확인 팝업의500ms 진행은 직접 실행해 disabled·중앙 스피너·이름·폭을 확인했습니다. 서버에 저장하지 않는 예제입니다.
- typecheck·전체 lint·변경 파일 Prettier·local/prod 빌드·28개 HTTP/CSP 검사가 통과했습니다. 인증·날짜·데이터 처리는 바꾸지 않았으므로 앞선 foundation10개·인증17개 이력은 별도로 유지하며 이번 버튼 검사로 재실행했다고 표시하지 않습니다.

이번 증거는 저장소 밖 `button-alignment` 폴더에 보관합니다. `before/{1440,390}-forms.png`, `after/{1440,390}-forms.png`, `after/{1440,390}-forms-loading.png`, `after/{1440,390}-login.png`, `after/{1440,390}-confirm-loading.png`, `after/{1440,390}-fixture.png`와 `before-pixel-bounds.json`, `fixture-result.json`, `page-results.json`, `live-submit.json`, `confirm-results.json`, `http-result.json`입니다.

기존3000·13020·13021 서버와 검증 사본은 보존했습니다. 버튼 수정의 새 검증 사본은 local13030/prod13031입니다. 기존3000은 소스 HMR 또는 새로고침으로 확인할 수 있습니다. 앞선13020은 버튼 수정 전 비교용 빌드이므로 최신 버튼 결과는13030을 봅니다. UIREF01~19와 기능54개 상태, API 생성 기본 경로·생성36개 파일은 유지합니다.
