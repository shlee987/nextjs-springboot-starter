# MyBatis

Mapper XML을 설명 예시의 기본으로 하되 기존 annotation·provider 방식을 강제 전환하지 않는다.
쿼리 목적과 복잡한 SQL 내부의 주석은 `style.md`의 주석 규칙을 따른다.

## Mapper 계약

- Mapper 메서드와 XML statement id는 SQL 동작을 나타내는 select/insert/update/delete로 시작하고 서로 같은 camelCase 이름을 사용한다. 기존 프로젝트·프레임워크 계약은 우선한다.
- SELECT는 selectUser, selectUserByEmail, selectUserListByStatus, selectUserCountByStatus처럼 대상·결과·조건을 드러낸다. 단건은 대상 이름을 사용하고 목록 조회는 대상 이름 바로 뒤에 List를 붙인다. 조건이 있으면 selectUserListByStatus처럼 List 다음에 By조건을 두며 단순한 복수형 s로 목록을 구분하지 않는다. 집계는 Count·Total 등 결과 의미를 표현한다. INSERT·UPDATE·DELETE는 insertUser, updateUserStatus, deleteUser처럼 실제 변경을 드러낸다.
- XML namespace는 연결할 Mapper 인터페이스의 전체 이름을 사용한다. 파라미터 이름과 반환 타입은 Java 메서드·SQL 바인딩·결과 매핑 계약에 맞춘다. Java 메서드·XML id·파라미터 이름에는 SQL 식별자 대문자 규칙을 적용하지 않는다.
- Mapper 입력을 모델로 받을지 개별 파라미터로 받을지는 `../common/design.md`의 모델 선택 기준을 따른다.
- INSERT·UPDATE·DELETE Mapper 메서드는 영향받은 행 수를 int로 반환한다. 반환 행 수의 사용·업무 판단은 Service에서 수행한다. 기존 프레임워크 계약이나 별도 결과 반환·배치 실행 방식이 있으면 해당 계약을 따른다.
- get/find/getRequired는 Java 업무 조회 메서드의 역할 이름이며 SQL에 직접 연결하는 Mapper에는 강제하지 않는다. 필수 조회의 예외는 Java 업무 조회 경계에서 기존 오류 체계로 처리한다. 이름만 다른 동일 SQL이나 단순 전달용 업무 메서드를 불필요하게 생성하지 않는다.

## 값 바인딩과 동적 식별자

입력값은 #{...}로 바인딩한다. ${...}에 사용자 입력을 직접 전달하지 않는다. 동적 식별자가 필요하면 허용된 값과 고정 SQL 분기로 제한한다.

## 동적 SQL

- 독립적인 선택 조건은 if, 여러 분기 중 하나를 선택할 때는 choose/when/otherwise를 사용한다. 태그를 불필요하게 중첩하거나 조건 조합별로 동일 쿼리를 복제하지 않는다.
- WHERE 조건 전체가 동적이면 where로 첫 AND/OR와 WHERE 생성을 처리한다. 고정 조건이 있으면 일반 WHERE에 선택 조건을 추가할 수 있다. 단순히 접속사를 붙이기 위해 WHERE 1 = 1을 추가하지 않는다.
- 목록 바인딩은 foreach와 #{...}를 사용한다. collection은 실제 파라미터 계약에 맞추고 open/separator/close로 구문을 구성한다.
- SET 생성과 끝쪽 쉼표 제거에는 set, 앞쪽 쉼표 등 별도 구문 정리에는 trim을 사용한다. 이 세트의 앞쪽 쉼표 배치에 맞는 동적 UPDATE는 trim prefix="SET" prefixOverrides=","를 기본으로 한다.
- 필수 조건은 입력 경계에서 검증하고 SQL에서 선택 조건처럼 생략하지 않는다. 특히 수정·삭제의 대상 조건 누락이 전체 데이터 변경으로 이어지지 않게 한다.
- null·빈 목록은 선택 필터 생략인지 대상 없음인지 호출 계약에 맞춰 처리한다. 대상 없음은 빈 결과·명시적 실패 등 계약대로 처리하고 조건 제거로 범위를 확대하지 않는다. nullable 옵션만으로 빈 입력의 업무 의미가 처리된다고 가정하지 않는다.
- 수정값의 null이 미수정인지 NULL로 변경인지 구분한다. 변경 컬럼이 하나도 없을 때는 기존 계약에 따라 처리하고 빈 SET을 실행하지 않는다. 이미 검증한 조건은 계층마다 반복 검사하지 않는다.

```xml
<!-- 상태·이름 조건에 맞는 사용자 목록 조회 -->
<select id="selectUserList" resultType="User">
    SELECT
        U.USER_ID
        , U.USER_NAME
    FROM USERS U
    <where>
        <if test="status != null">
            AND U.STATUS = #{status}
        </if>
        <if test="name != null and name != ''">
            AND U.USER_NAME = #{name}
        </if>
        <if test="maxAge != null">
            AND U.AGE &lt;= #{maxAge}
        </if>
    </where>
    ORDER BY U.USER_ID
</select>

<!-- 전달된 사용자 속성만 변경 -->
<update id="updateUser">
    UPDATE USERS
    <trim prefix="SET" prefixOverrides=",">
        <if test="name != null">
            , USER_NAME = #{name}
        </if>
        <if test="status != null">
            , STATUS = #{status}
        </if>
    </trim>
    WHERE USER_ID = #{userId}
</update>
```

조회 예시는 선택 조건이 모두 없으면 전체 사용자 목록을 조회하는 계약이다. UPDATE 예시는 null을 미수정으로 사용하며 호출 전에 필수 userId와 변경 항목 존재를 검증하는 계약이다. 타입·입력·정렬은 실제 기능에 맞춘다.

## 결과 매핑

- 결과는 resultType과 자동 이름 매핑을 기본으로 한다. 기존 설정을 확인하고, 이름이 다른 컬럼·계산 결과·JOIN의 중복 컬럼은 AS 별칭으로 반환 모델의 프로퍼티에 맞춘다. 이름 차이만을 위해 resultMap을 만들지 않는다.
- mapUnderscoreToCamelCase가 활성화된 경우 USER_ID는 userId에 대응한다. 비활성화된 경우 기존 설정을 임의로 바꾸지 않고 해당 설정에서 매핑 가능한 별칭을 사용한다. 필요한 매핑 정보는 관련 설정이 바뀌지 않으면 재사용한다.
- 별칭만으로 표현하기 어려운 중첩 객체·컬렉션·생성자 매핑 등은 필요한 경우 resultMap을 사용한다. 반환 모델 선택은 `../common/design.md`의 모델 선택 기준을 따른다.

예: mapUnderscoreToCamelCase가 활성화되어 있으면 `U.DISPLAY_NAME AS USER_NAME`으로 userName에 매핑한다. 타입·별칭은 실제 반환 모델과 설정에 맞춘다.

## 공통 SQL 조각

- 쿼리는 한곳에서 읽히도록 직접 작성을 기본으로 한다. 공통 SQL은 동일한 규칙을 함께 유지해야 하고, 분리의 유지보수 이점이 참조·결합 비용보다 클 때만 sql/include로 도입한다.
- 짧은 중복은 허용한다. 컬럼 수·쿼리 길이·반복 횟수만으로 분리하지 않으며, 중첩 include나 분기·치환으로 서로 다른 쿼리를 억지로 공통화하지 않는다.
- 분리할 때는 역할이 드러나는 camelCase 조각 ID를 사용한다. 공통 조각 변경 시 실제 참조 쿼리의 영향 범위를 확인한다.

## XML 표기

- SQL 비교 연산자는 XML escape를 기본으로 한다. <·<=는 &lt;·&lt;=로, >·>=는 &gt;·&gt;=로 표기한다. XML 속성 안의 표현식에도 필요한 escape를 적용한다.
- CDATA는 escape보다 읽기 쉬운 특별한 구간에만 제한적으로 사용하고 동적 태그는 밖에 둔다. 단순 비교 연산자를 위해 CDATA를 추가하거나 쿼리 전체를 감싸지 않는다.

## 확인 범위

변경한 동적 조건의 실제 생성 SQL과 파라미터·매핑을 확인한다. 변경한 분기와 관련된 값 없음·빈 목록을 포함하고 무관한 모든 Mapper를 매번 검사하지 않는다. 일반 SQL 변경 검증은 `queries.md`를 따른다.
