# SQL·MyBatis 구성 검토

확인일: 2026-10-07. 합의한 SQL·MyBatis 규칙을 AGENTS.md와 README에 연결했다.

## 참고 자료

- [MyBatis Mapper XML](https://mybatis.org/mybatis-3/sqlmap-xml.html): 값 바인딩과 문자열 치환, statement·결과 매핑, 공통 SQL 조각.
- [MyBatis 동적 SQL](https://mybatis.org/mybatis-3/dynamic-sql.html): where/set/trim/choose/foreach와 생성 SQL 처리.
- [Simon Holywell SQL Style Guide](https://www.sqlstyle.guide/): 명확한 식별자, 키워드 대문자, 절·JOIN의 배치. 공식 표준이 아닌 작성자의 스타일이며 river 정렬·30바이트 이름 제한·테이블 집합명 강제는 그대로 채택하지 않는다.
- [UNICEF adt-studio AGENTS.md](https://github.com/unicef/adt-studio/blob/develop/AGENTS.md): 파라미터화된 SQL 지침.
- [AI-prompt-database AGENTS.md](https://github.com/DepressionCenter/AI-prompt-database/blob/main/AGENTS.md): 파라미터 바인딩과 SQL 논리 구간 설명 주석. 프로젝트 전용 보안·도구·승인 규칙은 가져오지 않는다.
- [Apache ShardingSphere AGENTS.md](https://github.com/apache/shardingsphere/blob/master/AGENTS.md): 한곳에서 읽히는 흐름, 참조 이동·불필요한 추상화 최소화 원칙을 SQL 조각 분리 기준에 맞게 재구성했다. Apache 재단 전체 표준이나 MyBatis 전용 규칙이라는 뜻은 아니다.

AI 예제는 검색에서 확인한 SQL 관련 지침 범위만 참고했다. 전체 지침의 효과나 독립적 검증 여부는 확인하지 않았다. Java 기반 MyBatis 조회 계약은 공식 문서에서 별도로 확인했다.

## 적용 구조

- SQL 작성·수정: docs/conventions/sql/style.md와 queries.md.
- MyBatis SQL·매핑 작성·수정: 추가로 docs/conventions/sql/mybatis.md.
- Java Mapper 메서드 작성·수정: 기존 Java 역할·주석 규칙도 적용.
- 일반 SQL에는 Java·React 제어문 빈 줄 규칙을 적용하지 않는다.

## 우선 확인할 선택

키워드 대문자, 4칸, 100자, 주요 절 줄바꿈을 추천한다. 사용자는 여러 줄 목록의 앞쪽 쉼표와 ORDER BY·GROUP BY의 단일 항목 한 줄·여러 항목 세로 배치를 선택했다.
사용자는 테이블·컬럼 이름과 SQL 별칭의 대문자 표기를 선택했다. 기존 스키마의 대소문자 구분과 Java 매핑 계약은 유지한다. DB별 추가 규칙 파일은 만들지 않고 공통 규칙을 유지한다.
사용자는 Mapper 메서드·XML id에 SQL 동작 기준의 select/insert/update/delete 접두사를 선택했다. 업무 조회의 get/find/getRequired와 적용 대상을 구분한다.
Mapper 목록 조회는 복수형 s 대신 대상 이름 바로 뒤의 List로 구분한다. 조건이 있는 경우 selectUserListByStatus처럼 List 뒤에 조건을 표기한다.
각 MyBatis 쿼리 위에는 한국어 XML 목적 주석을 두고, 복잡한 쿼리 내부에는 필요한 처리 의도를 한국어 /* ... */ 주석으로 설명하는 정책을 사용자가 선택했다.
동적 SQL은 MyBatis 공식 가이드의 if/choose/where/set/trim/foreach 동작을 기준으로 작성하도록 확정했다. 앞쪽 쉼표의 동적 SET은 trim으로 처리한다. 필수 조건·빈 목록·null 수정값의 의미는 공식 태그 기능과 별개로 이 세트의 호출 계약 기준을 적용한다.
사용자는 SQL 표현 전반에 목적에 맞는 내장 함수를 우선 사용하고 불필요한 분기·수식·서브쿼리와 과도한 함수 중첩을 피하는 기준을 선택했다. NULL 처리·반환 타입·결과 의미는 유지한다.
사용자는 결과 매핑을 자동 이름 매핑 기반으로 하고 이름 차이·컬럼 충돌은 SQL 별칭으로 맞추도록 선택했다. resultMap은 별칭만으로 표현하기 어려운 구조에 사용한다. [공식 설정](https://mybatis.org/mybatis-3/configuration.html)의 mapUnderscoreToCamelCase는 기본값이 false이므로 실제 설정을 확인한다.
사용자는 ShardingSphere의 참조 이동 최소화 원칙을 참고해 쿼리 직접 작성을 기본으로 하고, 같은 규칙을 함께 유지할 필요와 유지보수 이점이 참조·결합 비용보다 큰 경우에만 공통 SQL을 분리하도록 확정했다. 짧은 중복은 허용한다.
사용자는 Mapper XML 비교 연산자에 CDATA 대신 XML escape를 기본으로 선택했다.
INSERT·UPDATE·DELETE Mapper는 영향받은 행 수를 int로 반환하고 Service에서 사용 여부와 업무적 의미를 판단하도록 확정했다.
SQL 검증은 변경 쿼리·동적 분기·관련 빈 입력을 중심으로 하고 공통 조각 변경 시 참조 쿼리까지 확대한다. 실행 계획은 성능 문제나 영향이 큰 변경에서 확인한다.
