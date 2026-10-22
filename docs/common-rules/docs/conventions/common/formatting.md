# 제어문 전후 빈 줄

Java·React 및 관련 JS/TS 코드에 공통 적용한다. 프로젝트의 명시적 형식 설정이 있으면 우선한다.

- if/else, for, while/do-while, switch, try/catch/finally 등의 제어 블록과 return, break, continue, throw 등 흐름 이탈문은 앞뒤의 다른 문장과 빈 줄 한 줄로 구분한다.
- 함수·블록의 첫 문장 앞과 마지막 문장 뒤에는 빈 줄을 넣지 않는다. 앞뒤에 실제 코드가 있는 쪽만 띄운다. 여는 중괄호 다음이나 닫는 중괄호 직전에 빈 줄을 강제하지 않는다.
- 연결된 `} else {`, `} catch (...) {`, `} finally {`, `} while (...);`는 한 덩어리로 유지한다. 기존 빈 줄과 중복해 두 줄 이상 띄우지 않는다.
- 해당 구간을 설명하는 주석은 문장·블록과 함께 묶는다. 구분용 빈 줄은 설명 주석 앞에 두고 주석과 대상 코드 사이에는 넣지 않는다.
- switch의 case/default 라벨과 그 안의 코드도 같은 원칙을 따른다. 라벨 바로 다음 첫 문장 앞이나 분기의 마지막 문장 뒤를 불필요하게 비우지 않는다.

## 예시

```java
public Result process(Input input) {
    if (input == null) {
        return Result.empty();
    }

    Data data = convert(input);

    // 항목별 상태 갱신
    for (Item item : data.getItems()) {
        update(item);
    }

    Result result = buildResult(data);

    return result;
}
```

```ts
function processItems(items: Item[]) {
  for (const item of items) {
    inspect(item);

    if (item.isInvalid) {
      recordFailure(item);

      break;
    }

    save(item);
  }
}
```

코드는 배치를 보여주는 발췌 예시이며 타입·의존성 정의는 생략했다.
