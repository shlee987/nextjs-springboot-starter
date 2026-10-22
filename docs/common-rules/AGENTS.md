# 공통 개발 규칙

목적은 코드의 역할과 실행 흐름을 쉽게 이해하고, 수정할 위치와 관리 비용을 줄이는 것이다.
이 세트는 프로젝트에 독립적이다. 특정 업무, 버전, 라이브러리, 폴더 구조를 전제하지 않는다.
명시된 규칙을 코드 작성 시 적용하고, 변경 부분의 위반을 직접 수정한다.

## 적용과 우선순위

- 사용자의 명시적 요청과 실행 환경의 상위 지침을 따른다.
- 프로젝트의 명시적 계약·규칙·도구 설정과 이 세트가 충돌하면 프로젝트 기준을 우선한다.
- 언어별 기본 스타일과 보충 참조 조건은 해당 style 문서를 따른다. 적용되는 기준이 없는 항목만 주변 코드의 관례를 따르며 기존 코드의 실수를 그대로 복제하지 않는다.
- 추천 기본값은 별도 선택 질문 없이 적용한다. 프로젝트가 명시한 선택 정책이 있으면 우선한다.
- 원본 규칙은 한 문서에서만 관리한다. 다른 문서에는 내용을 복사하지 않고 참조한다.

## 문서 선택

DB 접속은 조회만 기본 허용한다. 직접·간접 DB 변경 실행은 `docs/conventions/common/database-access.md`의 명시적 승인 기준을 따른다.

경로는 이 파일이 있는 디렉터리를 기준으로 한다. 조건에 해당하는 문서만 읽는다.

| 작업 조건 | 읽을 문서 |
| --- | --- |
| 프로젝트 초기 구성, 인증·권한·입력 검증·암호화·비밀값·보안 정책 변경, 서버·DB·클라우드·CI/CD 구성, 보안 점검·취약점 수정 | `docs/conventions/security/guide.md` |
| 함수·클래스 추가·분리, 책임·호출 구조 변경, 입력·반환 모델 또는 파라미터 구성 선택·변경 | `docs/conventions/common/design.md` |
| 예외 처리, 실패 반환, 로그 추가·변경 | `docs/conventions/common/errors.md` |
| 설정·프로파일·빌드 옵션·상수·상태 값 추가·변경 또는 배치 선택 | `docs/conventions/common/values.md` |
| 함수 작성·수정, 주석 변경 또는 기존 주석이 있는 코드·쿼리 수정·이동 | `docs/conventions/common/comments.md` |
| 코드 변경 또는 완료 검증 | `docs/conventions/common/changes.md` |
| DB 접속 또는 DB를 사용하는 테스트·스크립트·애플리케이션 실행 | `docs/conventions/common/database-access.md` |
| Java 메서드·클래스 추가 또는 역할 변경 | `docs/conventions/java/roles.md` |
| Java 코드 작성·수정 | `docs/conventions/java/style.md` |
| Java·React 및 관련 JS/TS의 제어문·흐름 이탈문 작성·수정 | `docs/conventions/common/formatting.md` |
| React 함수·컴포넌트·Hook 추가 또는 역할 변경 | `docs/conventions/react/roles.md` |
| React 및 관련 JS/TS 코드 작성·수정 | `docs/conventions/react/style.md` |
| UI 요소·동작·표시 추가·변경 또는 클래스·인라인 스타일·CSS·테마·공통 스타일 적용·변경 | `docs/conventions/styles/common.md` |
| SQL 작성·수정 | `docs/conventions/sql/style.md`, `docs/conventions/sql/queries.md` |
| MyBatis Mapper 메서드·SQL·매핑 작성·수정 | `docs/conventions/sql/mybatis.md` |
| Java·React 및 관련 JS/TS 새 파일 생성 | 해당 언어의 style 문서와 `templates/README.md` |
| Spring 기반 코드 작성·변경 | `docs/conventions/java/frameworks.md`의 Spring 항목 |
| React 프레임워크 기반 코드 작성·변경 | `docs/conventions/react/frameworks.md`의 해당 항목 |

- 여러 조건에 해당하면 해당 문서를 함께 적용한다. 한 문서는 한 번만 읽는다.
- 이미 읽은 문서는 원본 변경, 작업 범위 확대, 관련 내용의 문맥 소실이 없으면 다시 읽지 않는다.
- 문서가 없거나 읽을 수 없으면 규칙을 추측하지 않고 해당 경로와 영향을 알린다.
- 완료 전 변경 부분에 적용되는 규칙만 대조한다. 무관한 기존 코드까지 정리하지 않는다.
- 코드 형식·함수 설명을 작성 시 적용하고, 기존 formatter가 있으면 대상 파일에 실행한다. 정리 작업을 사용자에게 남기지 않는다.
- 루트 README와 references/는 코딩 시 필수 참조가 아니다. 규칙 세트 자체를 개정할 때만 README의 개정 절차를 확인한다. 보안 작업의 공식 문서 선택은 docs/conventions/security/guide.md의 참조 조건을 따른다.

