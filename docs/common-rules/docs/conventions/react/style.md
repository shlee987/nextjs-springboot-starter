# React 및 JS/TS 스타일

## 네이밍·타입

- 프로젝트의 명시적 규칙·형식 설정을 우선하고, 그 외에는 이 세트의 규칙을 적용한다. 양쪽에 기준이 없는 항목만 주변 코드와 일치시킨다. 적용 범위는 작성·변경 부분으로 한정한다.
- React 컴포넌트와 타입은 PascalCase, 일반 함수·변수는 camelCase를 사용한다.
- TypeScript에서 JSX를 포함하는 파일은 .tsx를 사용한다.
- 새로운 컴포넌트는 함수 컴포넌트를 기본으로 한다. 기존 클래스 컴포넌트를 무관하게 전환하지 않는다.
- React.FC, memo, useMemo, useCallback을 일괄 적용하지 않는다. 필요한 타입 계약이나 실제 성능 근거가 있을 때 선택한다.
- TypeScript는 props·공개 함수의 입력·출력 계약을 명확히 하고, 로컬 값은 추론을 활용한다. any·강제 단언으로 확인되지 않은 데이터를 숨기지 않는다. 외부 입력은 경계에서 검증·좁히기 한다.
- boolean은 is/has/can, 컬렉션은 복수형, 단위가 있는 값은 delayMillis처럼 드러낸다. 함수 역할 접두사는 roles.md를 따른다.

## 형식·배치 기본값

- 별도 설정이 없으면 UTF-8·LF, 공백 2칸, JS/TS 작은따옴표, JSX 큰따옴표, 세미콜론, trailing comma를 기본으로 한다. 줄 길이는 100자 기준으로 줄바꿈하며 엄격한 문자열 자르기 제한으로 사용하지 않는다.
- 기존 formatter 설정이 있으면 우선하며 새 도구 도입이나 전체 파일 재정렬을 강제하지 않는다.
- 제어문·흐름 이탈문 작성 시 `../common/formatting.md`의 전후 빈 줄 규칙을 적용한다.
- 컴포넌트 파일명은 PascalCase, Hook은 useX, 일반 모듈은 camelCase를 기본으로 한다. 프레임워크 예약 이름·기존 명명 체계는 우선한다.
- 코드 배치는 기존 구조를 따르고 특정 기능 전용 코드는 해당 기능 가까이에 둔다. 공통 코드는 성격별 모듈로 묶고 무관한 기능을 하나의 utils 파일에 쌓지 않는다.
- import는 외부 모듈·내부 모듈·상대 경로 순으로 묶고 미사용 항목을 제거한다. 자동 정렬 설정과 부수 효과 import의 실행 순서를 우선한다.
- 컴포넌트 내부는 Hook·state, 파생 값, 이벤트 처리, 필요한 Effect, JSX 반환 순을 기본으로 하되 관련 로직은 함께 둔다. 일반 Hook은 조기 반환 전에 호출한다. API별 예외는 실제 버전의 규칙을 따른다.
- 클릭은 button, 이동은 링크 등 의미에 맞는 요소를 사용하고 입력 label·키보드 조작을 보존한다.

## JS/TS 문서 주석

작성 범위·언어·구간 주석은 `../common/comments.md`에 정의한다.
TS 타입을 주석에서 중복 선언하지 않는다. JS는 필요한 타입을 JSDoc으로 표현한다.

```ts
/**
 * 표시할 금액을 천 단위 구분자가 있는 문자열로 변환한다.
 * @param amount 원 단위 금액
 * @returns 표시용 금액
 */
export function formatAmount(amount: number): string {
  return new Intl.NumberFormat('ko-KR').format(amount);
}
```

props의 비자명한 의미는 해당 속성에 문서화하고 Hook은 입력·반환 동작·외부 동기화 조건을 설명한다. export 방식·props의 type/interface는 기존 프로젝트 관례를 우선한다.

