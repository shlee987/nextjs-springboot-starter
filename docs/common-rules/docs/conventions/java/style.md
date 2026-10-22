# Java 스타일

## 네이밍

- 프로젝트의 명시적 규칙·형식 설정을 우선하고, 그 외에는 이 세트의 규칙을 적용한다. 적용 범위는 작성·변경 부분으로 한정한다.
- Java 스타일은 [Google Java Style](https://google.github.io/styleguide/javaguide.html)을 기본으로 하되 이 세트에 명시한 변경 규칙을 우선한다. 일반 작업은 로컬 규칙과 프로젝트 설정으로 수행하고, 명시되지 않은 세부 스타일의 판단이 필요할 때만 원문의 해당 항목을 확인한다. 매 작업마다 원문 전체를 읽거나 검색하지 않으며 확인한 항목은 관련 변경·문맥 소실이 없으면 재사용한다.
- 프로젝트 설정·이 세트·Google Java Style에도 기준이 없는 항목만 주변 코드와 일치시킨다.
- 클래스·인터페이스는 UpperCamelCase, 메서드·필드는 lowerCamelCase를 사용한다.
- 상수는 UPPER_SNAKE_CASE를 사용한다. 단순히 final이라는 이유로 모든 변수를 상수 이름으로 쓰지 않는다.
- public 최상위 타입의 파일명은 타입명과 일치시킨다.
- 패키지는 소문자, 타입은 명사 중심, 메서드는 동작·판단 중심으로 이름 짓는다. 의미 없는 축약어·타입 접두사는 사용하지 않는다.
- boolean은 is/has/can 등 의미가 드러나게, 컬렉션은 복수형으로, 시간·크기는 timeoutMillis 같은 단위를 이름에 드러낸다. 프로젝트 용어와 널리 알려진 약어는 유지한다.

## 형식·배치 기본값

- 별도 설정이 없으면 UTF-8, 공백 4칸, 100자 줄 길이, 여는 중괄호를 같은 줄에 둔다. 들여쓰기는 Google 기본값 대신 이 세트가 선택한 4칸을 사용한다. 긴 URL·package·import 등 불가피한 줄은 예외다.
- 제어문·흐름 이탈문 작성 시 `../common/formatting.md`의 전후 빈 줄 규칙을 적용한다.
- if/else/for/while은 한 줄 본문에도 중괄호를 사용한다. 한 줄에 한 문장을 작성한다.
- wildcard·미사용 import를 추가하지 않는다. 기본 순서는 static import 그룹 후 일반 import 그룹이며 각 그룹을 정렬한다.
- 파일은 최상위 타입 하나를 기본으로 한다. 기존 패키지 구조를 사용하고 관련 업무 코드를 모은다. 임의의 새로운 계층·폴더를 만들지 않는다.
- 클래스 내부는 상수·필드, 생성자, 공개 동작, 내부 보조 처리 순서를 기본으로 하되 관련 메서드는 함께 배치한다. 오버로드는 인접하게 둔다.

## Javadoc

문서 주석의 작성 범위·한국어·구간 주석은 `../common/comments.md`에 정의한다.
첫 문장은 역할을 설명하고 필요한 계약만 태그에 적는다. 기존 상속 설명이 정확하면 `{@inheritDoc}`를 사용할 수 있다.

```java
/**
 * 입력 금액을 표시용 문자열로 변환한다.
 * @param amount 원 단위 금액
 * @return 천 단위 구분자가 포함된 금액
 */
public String formatAmount(long amount) {
    return NumberFormat.getIntegerInstance(Locale.KOREA).format(amount);
}
```

예시는 메서드 발췌이며 프로젝트의 import·타입·formatter에 맞춘다. 형식과 헤더는 프로젝트 설정으로 변경할 수 있는 추천 기본값이다.

