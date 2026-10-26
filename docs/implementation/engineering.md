# 공통 지침에서 도출한 구현 작업

이 문서는 규칙을 복제하지 않고 구현 산출물과 검수 책임으로 연결한다. 단계별 적용 범위는 [1차 세팅](initial-setup.md), 공통 응답은 [API 계약](api-contract.md)이 소유한다. 파일 경로는 향후 생성할 산출물이며 현재 존재를 뜻하지 않는다.

## 구현 작업 목록

| ID | 구현자가 만들거나 확정할 것 | 완료 조건 | 공통 근거 |
| --- | --- | --- | --- |
| ENG01 | frontend/, backend/, docs/ 경계와 기능별 폴더 설계 | Controller·업무·저장소·UI·외부 연동 책임과 의존 방향을 문서화. 무의미한 전달 계층·인터페이스 일괄 생성 금지 | common/design.md, java/frameworks.md |
| ENG02 | 모듈별 빌드·실행·검사 명령, 런타임 버전, lockfile 또는 의존성 검증 | 확정 버전표를 적용해 깨끗한 checkout에서 재현. 설치·잠금 그래프·실행 검사 증거 기록. 개발·검증·운영 실행 명령 구분 | common/changes.md |
| ENG03 | .editorconfig 및 실제 formatter·lint 설정 | Java 4칸, JS/TS 2칸, 100자 기준과 공통 빈 줄 규칙 호환. Google Java Style 기본 도구를 무검토 적용해 2칸으로 바꾸지 않음 | common/formatting.md, java/style.md, react/style.md |
| ENG04 | 한국어 함수·타입 설명과 파일 템플릿 사용 | 단순 이름 번역 대신 목적·중요 계약, 실제 부수 효과·실패 설명. 작성자·개인 경로 자동 삽입 없음 | common/comments.md, templates |
| ENG05 | 프론트 조회·오류·폼·상태의 단일 경로 | 로딩·빈 값·실패 구분, 오래된 응답 반영 방지, Error Boundary와 이벤트·비동기 오류 책임 구분 | react/roles.md, react/frameworks.md, common/errors.md |
| ENG06 | API 명세와 단일 오류 계약 | 요청·응답 DTO, 값 없음·필수 조회·페이징·오류 상태와 코드 문서화. 필터 오류도 동일 계약 | java/roles.md, common/errors.md |
| ENG07 | 검증된 설정 묶음과 환경변수 표 | 환경값·빌드값·상수·enum 분리. 미정·빈 필수값으로 운영 시작 실패 | common/values.md, security/configuration.md |
| ENG08 | DB 접근·트랜잭션·마이그레이션 계획 | 확정한 PostgreSQL·MyBatis·Flyway의 바인딩·제약·원자성·재실행·부분 실패 계약. AI의 변경 실행은 별도 승인 대상 | common/database-access.md, common/design.md |
| ENG09 | SQL/Mapper 규칙 적용 계획 | SQL 키워드·식별자·앞 쉼표, 목록·동적 SQL·매핑을 해당 원문과 대조. 확정한 MyBatis 단독 데이터 접근 계약 적용 | sql/style.md, sql/queries.md, sql/mybatis.md |
| ENG10 | 공통 UI·스타일·접근성 기준 | 역할별 스타일 재사용, 키보드·label·포커스, 긴 문구·좁은 화면·화면 상태 검증 | styles/common.md |
| ENG11 | 보안 요구사항 구현과 증거 연결 | SEC·FE·HDR·API·CFG·OPS·CI ID별 설정 위치·거부 동작 검증 | security/baseline.md 이하 |
| ENG12 | README 실행 안내와 변경 검증 | 실제 명령·필수 환경·공개용 예제·검증 한계 기록. 변경 범위 검사부터 수행 | common/changes.md |

공통 근거의 경로는 [conventions](../common-rules/docs/conventions/) 기준이다. templates는 [파일 템플릿](../common-rules/templates/README.md)이다.

## API·권한 명세 양식

각 기능 API 설계에는 메서드·경로·인증·역할·리소스 소유권·필드 타입/필수/길이/범위·null 의미·허용되지 않는 필드·응답 DTO·상태코드·오류코드·페이지 상한·트랜잭션·중복 요청 정책·감사 이벤트·보안 검증 ID를 적는다.

공통 성공/오류/페이지 모델과 반환 편의는 1차부터 구현한다. 로그인·현재 사용자·CSRF 발급·로그아웃의 제품 경로와 payload는 인증 단계에서 별도 API 명세로 고정한다. 작업자가 URL·토큰 저장 방식·에러 래퍼를 각자 만들지 않는다.

## 구성 위치 기본안

| 책임 | 예정 위치·방향 |
| --- | --- |
| 프론트 라우팅 | frontend/src/app, 예약 파일은 Next 규칙 우선 |
| UI·업무 기능 | 기능 전용은 해당 기능 가까이, 공유는 역할별 모듈 |
| 브라우저 API 호출 | 공통 요청 경계 하나. 인증·CSRF·타임아웃·오류 계약 일원화 |
| Next 서버 API 호출 | server-only 경계, 고정 backend 목적지, 최소 DTO |
| 백엔드 | backend의 업무별 패키지, web·service/domain·repository 책임 구분 |
| 설정·보안 | 각 실행 모듈의 설정 영역. 애플리케이션과 프록시의 소유권은 헤더 설계로 구분 |
| 공개 문서 | docs. 개인 작업 로그·브라우징 임시자료는 저장소 밖 |

빌드 도구·데이터 기술·버전은 [확정 버전표](stack-versions.md)를 적용한다. 세부 패키지명과 예제 기능은 해당 후속 설계에서 정한다. 공통 규칙과 상충할 때 이 표를 근거로 임의의 예외를 만들지 않는다.
