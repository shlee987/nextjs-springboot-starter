# MyBatis 데이터 접근 설계

상태: MyBatis 단독 사용은 사용자 확정. 설정·예제 구현·실제 DB 검증 전. PostgreSQL 제품·버전과 라이브러리 조합은 [확정 버전표](stack-versions.md)를 따른다.

이 문서는 현재 프로젝트의 공통 계약이다. 모든 프로젝트에 적용하는 공통 룰이 아니며, docs/common-rules에 복사하지 않는다.

## 역할과 SQL 작성

- CRUD·검색·JOIN·통계·임시 테이블 작업을 MyBatis로 구현한다. Mapper와 SQL 매핑으로 실행할 쿼리를 명시한다.
- 서비스는 업무 로직과 트랜잭션 경계, Mapper는 SQL 실행과 결과 매핑을 담당한다. 요청·응답 DTO와 DB 조회 모델의 역할을 구분한다.
- SQL·Mapper 작성은 공통 룰의 docs/conventions/sql/style.md, queries.md, mybatis.md를 적용한다. 이 경로들은 docs/common-rules 기준이다.
- 사용자 값은 바인딩 파라미터로 전달한다. 동적 정렬·식별자는 허용 목록으로 매핑한다. 사용자 입력을 SQL 문자열에 직접 삽입하지 않는다.
- 페이지 크기 상한·정렬 안정성·조회 컬럼·인덱스·실행 계획을 조회 계약에 포함한다. 목록의 행마다 추가 조회하는 패턴을 피한다.
- 동시 수정 보호가 필요한 업무는 version 조건부 UPDATE 또는 적합한 잠금을 설계하고 영향 행 수를 검사한다. 감사 필드 갱신도 SQL/서비스 계약에 명시한다.
- MyBatis·starter·Spring Boot·JDK·JDBC driver 선정 버전은 확정 버전표를 적용하고 실제 설치 그래프·연결은 구현 시 검증한다.

## 트랜잭션 경계

- 트랜잭션은 서비스의 업무 단위 메서드에 @Transactional로 명시한다. 패키지·메서드명 AOP나 모든 서비스 클래스에 일괄 적용하지 않는다.
- 변경 작업과 여러 SQL의 원자성·동일 연결·일관된 조회가 필요한 범위를 묶는다. 단순 단일 SELECT마다 서비스 트랜잭션을 기계적으로 추가하지 않는다.
- 조회 트랜잭션의 readOnly는 의도·최적화 힌트이며 쓰기 차단 보안 수단이 아니다. 격리 수준에 따라 여러 SELECT의 동일 시점 데이터가 자동 보장되지는 않는다.
- 임시 테이블 생성·입력·삭제가 있으면 최종 반환이 조회여도 일반 쓰기 트랜잭션을 사용한다.
- 기본 전파는 REQUIRED다. REQUIRES_NEW·비동기·다른 스레드는 동일 연결 공유 범위를 벗어나므로 임시 테이블 처리 중 사용하지 않는다.
- 실제 트랜잭션 진입은 Spring 프록시를 통한 호출로 구성한다. 같은 클래스 내부 호출만으로 새 트랜잭션이 시작된다고 가정하지 않는다.
- 외부 API·파일 처리·긴 계산은 가능한 한 DB 트랜잭션 밖에 둔다. 예외를 삼켜 부분 성공을 정상 반환하지 않는다.
- 원자적 변경 업무는 checked exception도 롤백하도록 rollbackFor = Exception.class를 명시하는 기본안을 사용한다. 부분 성공 업무는 별도 계약을 작성한다.

## 연결과 실행 설정

- Spring JDBC 트랜잭션 매니저와 MyBatis SqlSessionFactory는 동일한 DataSource bean을 사용한다. 같은 URL의 별도 pool은 동일 연결이 아니다.
- JDBC 로컬 트랜잭션은 JdbcTransactionManager 기본안을 사용한다. 구현 시 실제 Boot 자동 설정이 만드는 매니저와 후보 수를 확인하고 하나의 명확한 기본 매니저로 확정한다.
- MyBatis-Spring의 Spring 관리 Mapper/SqlSessionTemplate과 SpringManagedTransactionFactory를 사용한다. 직접 openSession, 별도 getConnection, 수동 commit/rollback/close로 경계를 우회하지 않는다.
- 기본 executor는 SIMPLE이다. SQL은 Mapper 호출 시 실행하며 커밋까지 대기하는 SQL 배치를 기본 사용하지 않는다.
- MyBatis BATCH executor를 추후 도입하면 실행 지연·flushStatements·오류 시점·연결/세션 제약을 별도 설계한다.
- 기본 예제는 MyBatis 2차 캐시를 비활성화하고 localCacheScope=STATEMENT를 사용한다. 캐시 추가 시 범위·무효화·일관성 검증을 별도로 정의한다.
- DB schema는 선택한 migration 도구가 단독 관리한다. 별도 자동 DDL 경로를 병행하지 않는다.
- 트랜잭션 밖의 Mapper 변경은 자동 커밋될 수 있으므로 원자적 업무에서 서비스 트랜잭션 누락을 검증한다.

## 임시 테이블 계약

- 생성부터 마지막 사용·삭제까지 동일 실제 DB 세션/연결에서 실행한다. Mapper 메서드가 나뉘어도 연결이 유지되어야 한다.
- 현재 PostgreSQL의 임시 테이블 수명과 commit 규칙을 아래 기준으로 적용한다. 고객 DB로 교체하면 드라이버·SQL 실행 범위·DDL의 암묵적 commit 여부를 다시 확인한다. @Transactional이 DB 고유 규칙을 바꾸지는 않는다.
- session 수명의 테이블은 pool 반환 후 남을 수 있으므로 DB의 트랜잭션 종료 삭제 기능 또는 명시적 정리와 실패 시 정리 방식을 정한다. 다음 요청에 이전 데이터가 노출되거나 같은 이름으로 충돌하지 않아야 한다.
- 임시 테이블을 위해 전역 공유 테이블이나 고정 공용 데이터를 만들어 연결 문제를 우회하지 않는다. 추가 DDL 권한은 선택 DB의 필요한 임시 영역에 한정한다.

### PostgreSQL 임시 영역과 권한 구분

PostgreSQL은 TEMP 테이블의 기본 수명이 DB 세션이며 ON COMMIT 기본값은 PRESERVE ROWS다. 업무 트랜잭션 안에서만 사용할 때는 ON COMMIT DROP을 기본 방향으로 잡고, autocommit이나 중간 commit으로 테이블이 먼저 사라지지 않도록 한다. 같은 물리 연결·명시적 업무 트랜잭션·정상 종료/실패 후 pool 재사용을 DB01·DB04·DB05로 검증한다.

업무 코드의 영구 schema DDL은 계속 금지한다. local 예제의 시작 시 Flyway migration만 단일 테스트 계정으로 허용하고, dev/stg/prod는 별도 migration 실행·권한 경계를 유지한다. 임시 테이블이 필요한 기능에는 DB의 TEMPORARY 권한을 별도로 명시하며 관리자나 영구 schema CREATE 권한으로 대신하지 않는다. PostgreSQL의 PUBLIC 기본 권한에 기대지 않고 bootstrap 권한표에 기록한다. 이는 Flyway의 영구 스키마 소유와 다른 경계다. 실제 예제 SQL·권한 부여는 후속 구현 범위이며 이번 검토에서 실행하지 않는다.

근거: [PostgreSQL 18 CREATE TABLE](https://www.postgresql.org/docs/18/sql-createtable.html), [DB 권한](https://www.postgresql.org/docs/18/ddl-priv.html).

## 구현 후 검증 기준

| ID | 실제 DB에서 확인할 결과 |
| --- | --- |
| DB01 | 하나의 서비스 트랜잭션에서 여러 Mapper의 DB 세션 ID가 같음. Java proxy 객체 주소 비교로 대체하지 않음 |
| DB02 | Mapper INSERT/UPDATE 다음 SELECT에서 변경 결과 확인 |
| DB03 | 여러 Mapper 변경 뒤 오류 발생 시 전체 롤백. RuntimeException과 checked exception 각각 확인 |
| DB04 | 여러 Mapper에서 임시 테이블 생성·입력·조회·삭제 성공. 선택 DB가 지원하는 트랜잭션 수명 조건 검증 |
| DB05 | 임시 테이블 정상 종료·중간 실패 후 pool 연결 재사용 시 이전 데이터·이름 충돌이 없음 |
| DB06 | 조건부 변경·잠금이 필요한 예제의 동시 요청에서 충돌 감지·영향 행 수 확인 |
| DB07 | 동적 검색·정렬·페이징의 바인딩과 허용 목록·페이지 상한 검증 |

검증은 격리된 실제 DB와 합성 데이터로 수행하고 공통 DB 변경 승인 기준을 따른다. H2 대체만으로 통과 판정하지 않는다. 지금은 문서만 작성했으며 DB 실행·검증은 수행하지 않았다.

## 공식 근거

- [MyBatis-Spring 트랜잭션과 DataSource 조건](https://mybatis.org/spring/transactions.html)
- [MyBatis 실행·캐시 설정](https://mybatis.org/mybatis-3/configuration.html#settings)
- [Spring JDBC 트랜잭션 매니저](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/jdbc/support/JdbcTransactionManager.html)

공식 자료는 기능 동작의 근거다. 역할 분담·명시적 서비스 트랜잭션·캐시 기본안은 이 프로젝트의 선택이다.
