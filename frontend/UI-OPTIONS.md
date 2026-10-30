# 공통 컨트롤 보완 UIOPT01~09

2026-10-30 사용자 승인 범위입니다. UIREF01~19·버튼 중앙 정렬과 기존 기능54개의 이력은 [UI 점검표](UI-REVIEW.md)·[기능표](FEATURES.md)에 보존합니다. 아래 검색·저장·실패 예제는 화면 안에서 응답을 제어하며 새 서버 API를 호출하지 않습니다.

## 구현과 사용처

| ID      | 구현 파일 (`src/` 기준)                                                        | 실제 사용·예제                                                                                                                                                                                     |
| ------- | ------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| UIOPT01 | `components/ui/Selections.tsx`, `ui.module.css`                                | Select placeholder/emptyMessage, 긴 항목 줄바꿈·목록 내부 스크롤. `/examples/selections`의 담당 부서30개/빈 목록과 폼 분류에서 사용                                                                |
| UIOPT02 | `providers/useOptionSearch.ts`, `components/ui/Selections.tsx`                 | 로컬 배열 필터와 제어된 검색을 구분. 검색 전/대기·로딩/성공/빈 결과/실패/재시도, 최소2글자·250ms 대기·AbortSignal·최신 결과 유지. 부서 검색은 실제 API 연동이 아닌 제어된 예제                     |
| UIOPT03 | `components/ui/Selections.tsx`, `ui.module.css`                                | MultiSelect 선택 개수·개별 해제 태그·최대 수·검색 결과 전체 선택. 기본 예제 최대2개·참여 부서 최대3개에서 사용. 로드된 검색 결과의 활성 항목만 전체 선택                                           |
| UIOPT04 | `components/ui/Selections.tsx`, `Icon.tsx`                                     | Checkbox의 `boolean \| 'indeterminate'`, mixed 의미와 가로선. MultiSelect 검색 결과 전체 선택에 실제 연결. 기존 onChange boolean 유지                                                              |
| UIOPT05 | `components/ui/Inputs.tsx`, `Form.tsx`, `ui.module.css`                        | prefixAddon/suffixAddon, 글자 수, Textarea 자동 높이·최대320px/내부 스크롤·폭/폰트 변화 대응, 오류 요약/필드 ref 이동. 입력·폼 예제와 로그인/비밀번호 폼의 오류 요약에서 사용                      |
| UIOPT06 | `components/ui/DatePickers.tsx`, `features/examples/ControlExamples.tsx`       | 기존 달력·UTC/서울·밀리초·키보드 보존. 선택 제한을 직접 편집에도 적용, 외부 값 변경 시 오래된 편집/오류 폐기, 빠른 기간도 정밀도·최소/최대·31일·선택 제한 검사. 주말 제한·예제 기준7/30일에서 사용 |
| UIOPT07 | `components/ui/Overlays.tsx`                                                   | 확인 문구·primary/danger 의도·비동기 완료 후 닫기·실패 오류와 입력 유지. 선택 적용은 primary/자동 닫기, 입력 초기화는 명시 danger/초기화 문구                                                      |
| UIOPT08 | `components/ui/Feedback.tsx`, `features/examples/ControlExamples.tsx`          | EmptyState의 조건 초기화로 실제 예제 결과 복원, ErrorState의 선택 재시도·진행·진단 ID. 기존 Toast 중복/시간/닫기와 안전한 오류 변환 유지                                                           |
| UIOPT09 | `lib/action-scope.ts`, `providers/ActionProvider.tsx`, `ScreenActionScope.tsx` | 키별 즉시 잠금·중복 무시·공유 상태·다른 키 병렬·취소 종료 후 해제. 예제 폼 제출·공통 확인창·재시도에 실제 연결. 버튼 메뉴의 두 저장 버튼·연속 두 번·다른 키·실패·취소·범위 전환에서 체험           |

라디오 안쪽 원은10px에서12px로 키웠습니다. 바깥20px 원·흰 배경·테두리는 유지해 안쪽 여백이 남습니다.

## 작업 실행 계약

앱의 ScreenActionScope는 인증 세대와 경로로 범위를 나눕니다. 중첩 ActionProvider도 부모 범위가 바뀌면 교체됩니다. 인증 세대는 React 재렌더 전에도 읽기 전용 현재 값으로 검사합니다. StrictMode의 즉시 해제/재연결은 실제 화면 종료와 구분합니다.

- 같은 작업에 고정 키를 사용합니다. 키에 비밀번호·토큰·개인정보 본문을 넣지 않습니다.
- 첫 run 호출이 렌더를 기다리지 않고 잠급니다. 처리 중 같은 키는 실행하거나 대기열에 넣지 않고 `{status: 'ignored'}`를 반환합니다.
- 성공은 `{status: 'completed', value}`, 취소·만료된 범위는 `{status: 'cancelled'}`입니다. 일반 동기/비동기 실패는 원래 오류로 reject하므로 호출자가 기존 오류 경계에서 처리합니다. 공통 계층이 오류를 성공이나 별도 Toast로 바꾸지 않습니다.
- cancel은 AbortSignal만 보냅니다. 작업 Promise가 실제로 끝나기 전까지 키와 진행 상태를 유지합니다. 서로 다른 키는 병렬로 실행합니다.
- 종료 시 자기 실행의 잠금만 해제합니다. 이전 범위의 늦은 완료/finally가 새 범위의 잠금을 풀거나 completed 값을 전달하지 않습니다.
- 실행 함수는 처리 결과를 반환하고, UI 반영은 completed 분기에서 합니다. 실제 비동기 처리는 완료 Promise를 반환하며 가능한 요청에 AbortSignal을 전달합니다.
- 보호 범위는 현재 브라우저 실행입니다. 서버 중복 처리·탭 간 정책은 해당 서버/인증 계약이 소유합니다. 기존 인증 refresh/single-flight와 Query 재시도·오류 정책은 그대로 유지합니다.

```tsx
const save = useAction('item:save:100');

async function handleSave() {
  try {
    const result = await save.run((signal) => saveItem(signal));
    if (result.status === 'completed') updateView(result.value);
  } catch (error) {
    showExistingError(error);
  }
}

<Button loading={save.pending} onClick={() => void handleSave()}>
  저장
</Button>;
```

saveItem/updateView/showExistingError는 호출자가 제공하는 작업·기존 표시 경계입니다. 새 공통 서버 API를 뜻하지 않습니다. ConfirmDialog의 autoClose도 onConfirm이 반환한 Promise가 성공한 뒤에만 동작합니다.

## 검증과 증거

- `node scripts/verify-actions.mjs`: 재렌더 전 같은 키 무시, 다른 키 병렬, 동기/비동기 예외의 원본 보존·잠금 해제, 취소 요청 이후 실제 종료까지 유지, 이전 finally와 새 범위 분리, StrictMode 재연결, 인증 세대 즉시 변경, 화면 해제 직후 늦은 완료를 확인했습니다.
- 기존 React/esbuild로 만든 저장소 밖 회귀 화면: StrictMode에서 동일 키 두 구독자의 상태 공유, 중첩 범위 전환 후 이전 완료가 새 잠금을 해제하지 않음, 취소를 무시하고 늦게 도착한 검색 A 응답이 최신 B 결과를 덮지 않음을 확인했습니다. 콘솔 경고/오류0개입니다.
- 선택 예제: 일부→전체→태그 개별 해제 후 일부로 복귀, 검색 입력 포커스 복귀, 최대 수 도달 시 추가 선택 차단, 긴 항목 End/Enter 선택·목록 내부 스크롤, 검색0/1/다수·실패 후 재시도를 확인했습니다.
- 입력·폼: 단위/지우기, 오류 요약2개와 이름/분류 ref 이동,20줄 설명의320px 제한·내부 스크롤, 초기화 뒤 빈 값/84px 복귀를 확인했습니다. 글자 수·프로그램 초기화 예제는 controlled value를 사용합니다. 자동 높이는 화면 폭과 글꼴 로드 뒤에도 다시 계산합니다.
- 날짜·확인·상태: 주말을 텍스트로 입력해도 거부, 빠른 기간으로 잘못 편집한 시작 값 초기화, 비동기 확인 실패 후 메모 유지·다시 성공하면 닫힘을 확인했습니다. 초기화 확인창은 위험 의도를 명시합니다.
- foundation10개·기존 인증 회귀17개가 PASS입니다. 서버 계정·비밀번호 변경, 실제 서버 검색·저장 통합 성공을 뜻하지 않습니다.

로컬·공유 환경 생산 빌드, 타입·전체 lint를 통과했습니다. 로컬14개 예제200/공유14개 예제404, nonce 변경·엄격 CSP·no-store·SSR inline style0개·로컬 폰트 응답 검사는28건 PASS입니다. 일반 화면16개×PC/모바일32건과 변경7개 화면의320~1920px49건에서 가로 넘침이 없었습니다. 선택 팝업의 긴 항목은 입력 폭에 맞춰 PC216.875px/모바일208.875px로 줄바꿈하며, 모바일 라디오는 바깥20px·안쪽12px입니다. 빈 로그인 제출의 오류 요약2개도 자격 정보 입력 없이 확인했습니다.

공개 저장소 밖 `ui-options` 작업 폴더에 `http-result.json`, `normal-views.json`, `breakpoints.json`, 상태별 회귀 JSON과 빌드·lint 로그를 보관합니다. `after/`의 PC/모바일 일반 화면32장, `1440-long-select.jpg`, `390-long-select.jpg`, `390-radio.jpg`, `1440-login-summary.jpg`가 추가 증거입니다. 이미지가 모든 상호작용·보호 화면의 서버 데이터 검증을 대신하지는 않습니다.

## 범위와 남은 검수

Tree/Stepper/업로드·새 UI 프레임워크·독립적인 장시간 작업·새 서버 API는 추가하지 않았습니다. 날짜의 기존 UTC 세 형식·서울 DST·연도0001~9999·최소/최대·정밀 숫자 계약을 유지합니다. 로그인 오류 요약은 자격 정보 입력 없이 확인하며 비밀번호 폼은 공통 ref/오류 코드 적용 검토와 기존 검증 범위를 구분합니다. 실제 비밀번호 변경은 이번 범위가 아닙니다.

기존 200% 확대·native 이탈 확인 도구 한계와 UI 옵션 검수 당시의54개 중42 verified/12 implemented 이력을 보존합니다. 최신 마감 상태는 FEATURES.md가 소유합니다. 비밀번호 변경은 이번 추가 검수로 완료 처리하지 않았습니다. 독립 리뷰는 조정자가 별도로 진행합니다.

## 독립 리뷰 파일 범위

버튼 정렬 완료본과 비교한 UIOPT 수정 범위입니다. `src/app/layout.tsx`는 추적 파일이며 아래 나머지 소스와 `scripts/verify-actions.mjs`는 현재 미추적 상태도 포함합니다. 기존 미추적 파일도 리뷰에서 빠뜨리지 않아야 합니다. `Button.tsx`의 중앙 정렬 구현은 그대로 보존했습니다.

- `src/app/layout.tsx`, `src/app/examples/[slug]/page.tsx`
- `src/components/ui/DatePickers.tsx`, `Feedback.tsx`, `Form.tsx`, `Icon.tsx`, `Inputs.tsx`, `Overlays.tsx`, `Selections.tsx`, `ui.module.css`
- `src/features/auth/AuthScreens.tsx`
- `src/features/examples/ActionExample.tsx`, `ControlExamples.tsx`, `exampleDelay.ts`, `ExampleSection.tsx`, `FormsExample.tsx`, `SelectionOptionsExample.tsx`
- `src/lib/action-scope.ts`, `src/providers/ActionProvider.tsx`, `ScreenActionScope.tsx`, `useOptionSearch.ts`
- `scripts/verify-actions.mjs`, `UI-OPTIONS.md`, `UI-REVIEW.md`, `FEATURES.md`, `README.md`, `CHANGELOG.md`

이후 승인된 파일 업로드·출력 경계의 실제 API 연결과 검증은 [SECFILE.md](SECFILE.md)에서 별도로 관리합니다. 마감 검토 CLR1-002는 외부 날짜 값 교체 뒤 부모 검증 상태를 Effect로 동기화하도록 보완했습니다. 유효값 교체 시 오류 해제, 잘못된 외부 값/최소·최대·기간 순서/다른 endpoint 오류 보존을 실제 컴포넌트 회귀로 확인했습니다. 기존 UTC/서울·DST·연도 경계 foundation10개도 재통과했습니다.
파일 마감에서 LIVE-CLOSE-002도 보완했습니다. 인증 경계는 세대 변경 시 유지하고 FileUpload 선택/미리보기만 key로 교체하며 페이지·캐시를 별도 초기화합니다. 익명 확인 요청이 멈추는 실제 관찰과 로그아웃/지연 응답/두 번째 사용자 회귀 증거는 SECFILE.md의 마감 절을 따릅니다.
