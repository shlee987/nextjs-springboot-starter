# SQL 스타일
DB 종류·버전과 기존 스키마·formatter의 계약을 우선한다.

## 형식과 배치

기본값: 키워드 대문자, 공백 4칸, 100자 기준, 주요 절과 컬럼·조건을 줄 단위로 구분. 세로 정렬용 과도한 공백은 사용하지 않는다.

확정한 배치: 여러 줄 목록은 쉼표를 다음 항목의 줄 앞에 두고 첫 항목에는 붙이지 않는다. 짧은 한 줄 목록은 일반 쉼표 표기를 유지한다. ORDER BY·GROUP BY는 항목 하나면 절과 같은 줄, 여러 개면 절 다음 줄부터 항목별로 나열한다. SELECT의 배치에 맞추기 위해 단일 정렬·그룹 항목까지 줄바꿈하지 않는다. 이 배치 기준은 DB 종류와 무관하게 적용한다.

## 식별자와 별칭

- 테이블·컬럼 이름과 SQL 별칭은 대문자로 표기한다. 새 테이블·컬럼 이름은 UPPER_SNAKE_CASE를 기본으로 한다. 기존 스키마를 이름 변경하지 않으며 대소문자를 구분하는 기존 식별자는 정확한 표기를 유지한다.
- 테이블 별칭은 원래 이름이나 역할을 알아볼 수 있게 정한다. 단순한 JOIN은 U·D처럼 짧게 쓰고 복잡한 JOIN은 BUYER·SELLER처럼 역할을 구분한다. JOIN 쿼리의 컬럼에는 별칭을 붙여 소속을 명시한다. 테이블 단수·복수는 일괄 강제하지 않는다.
- Java 프로퍼티·MyBatis 파라미터 이름·XML 태그와 속성·문자열 값은 대문자 표기 대상이 아니다. 결과 컬럼 별칭이 Java 매핑 계약에 사용되는 경우 기존 계약을 유지한다.

```sql
SELECT
    U.USER_ID
    , U.USER_NAME
    , D.DEPARTMENT_NAME
FROM USERS U
INNER JOIN DEPARTMENTS D
    ON D.DEPARTMENT_ID = U.DEPARTMENT_ID
WHERE U.STATUS = #{status}
```

## 주석

기존 주석의 보존·갱신·이동은 `../common/comments.md`를 따른다.

- 각 MyBatis 쿼리 태그 바로 위에 한국어 XML 주석으로 조회·변경 목적을 한 줄 설명한다. Mapper Java 메서드의 문서 주석은 기존 공통 주석 규칙을 따른다.
- 복잡한 쿼리 내부의 JOIN·집계·서브쿼리·특이 조건에는 필요한 위치에 한국어 `/* ... */` 주석으로 처리 의도를 설명한다. 단순한 SELECT·FROM·WHERE 구간이나 자명한 컬럼·조건에는 주석을 반복하지 않는다.
- XML 주석은 쿼리 전체의 목적, SQL 내부 주석은 해당 처리의 이유를 설명한다. 같은 설명을 중복하지 않으며 동작 변경 시 관련 주석도 갱신한다.

```xml
<!-- 주문 이력이 있는 사용자별 최근 주문 시각 조회 -->
<select id="selectUserOrderSummaryList" resultType="UserOrderSummary">
    SELECT
        U.USER_ID
        , O.LAST_ORDER_AT
    FROM USERS U
    /* 주문을 사용자별 한 행으로 집계해 사용자 행의 중복을 방지한다. */
    INNER JOIN (
        SELECT
            USER_ID
            , MAX(CREATED_AT) AS LAST_ORDER_AT
        FROM ORDERS
        GROUP BY USER_ID
    ) O
        ON O.USER_ID = U.USER_ID
    ORDER BY U.USER_ID
</select>
```

예시는 주석 배치를 설명하기 위한 것이며 타입·스키마는 실제 프로젝트 계약을 따른다.
