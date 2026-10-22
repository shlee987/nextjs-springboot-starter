# 참고 사례와 채택 판단

## UI 스타일 적용 참고 (2026-10-07)

- [Pulumi 문서 지침](https://github.com/pulumi/docs/blob/master/AGENTS.md), [TinyWidgets 지침](https://github.com/tinyplex/tinywidgets/blob/main/AGENTS.md): 기존 공통 UI·스타일·공유 값 재사용을 참고했다. 프로젝트 전용 경로와 도구는 가져오지 않았다.
- [HeroUI 지침](https://github.com/heroui-inc/heroui/blob/v3/AGENTS.md), [Eventide Quill 지침](https://github.com/EventideMiles/eventide-quill/blob/main/AGENTS.md): BEM의 역할·요소·변형 구분과 기존 블록 확장·공유 스타일 중복 방지를 참고했다. 고정 파일 구조·접두사·전용 라이브러리는 제외했다.
- [Bootstrap용 재사용 지침 예제](https://gist.github.com/homestar9/af0c9db1916dfc8e6f2763b724185746): 자체 클래스와 프레임워크 클래스의 책임·이름 충돌 구분을 참고했다.
- [Bootstrap 타이포그래피](https://getbootstrap.com/docs/5.3/content/typography/), [Tailwind 테마 변수](https://tailwindcss.com/docs/theme), [유틸리티 스타일링](https://tailwindcss.com/docs/styling-with-utility-classes): 기본 스타일·공통 값·유틸리티 재사용과 동적 값의 인라인 적용 근거. 특정 버전을 요구하지 않고 프로젝트의 실제 방식을 따른다.

현재 작업 범위에서만 개선하고 전체 정리는 명시적 요청 시 진행하는 정책, 대체 정의의 교체 안내 주석은 사용자가 선택했다. BEM은 일반 CSS의 미정의 명명 방식에 대한 추천값이며 모든 스타일 도구에 강제하지 않는다.

- [MagentaA11y 지침](https://github.com/tmobile/magentaA11y/blob/main/AGENTS.md): 의미에 맞는 HTML·입력 label·키보드·포커스와 반응형·테마 검증 원칙을 참고했다. 프로젝트 전용 검사·매번 전체 체크리스트·모든 컴포넌트 테스트 생성 요구는 제외했다.
- [Eventide Quill 지침](https://github.com/EventideMiles/eventide-quill/blob/main/AGENTS.md): SCSS 원본과 생성 CSS 구분을 생성 파일 전반의 원본 수정 원칙으로 확장했다.
- [ShardingSphere 구현 기준](https://github.com/apache/shardingsphere/blob/master/.codex/skills/code-implementation/references/rules/implementation.md): 개발 환경의 절대 경로 하드코딩 방지와 프로젝트 기준 경로·설정 활용을 참고했다.

일반 코딩 작업의 필수 참조 문서가 아니다. 규칙 검토·개정 시 사용한다.
확인일: 2026-10-02. 링크 내용은 변경될 수 있다.
공개 사례의 반복은 참고 근거이며 효과를 보장하는 실험 결과가 아니다.
독립 작성·복사 관계는 검증하지 않았으므로 반복 횟수를 품질 점수로 사용하지 않는다.

## 공통 원칙

- [Anbeeld 글로벌 지침](https://github.com/Anbeeld/AGENTS.md/blob/main/AGENTS.md): 최소 변경, 기존 자산 재사용, 위험에 비례한 확인·검증. 위임·운영·승인 절차는 이 세트에 복사하지 않았다.
- [Austin1serb 지침](https://github.com/Austin1serb/agents-md/blob/main/AGENTS.md): 단순함, 래퍼 방지, 참조 범위 제한. 일회성 함수의 일괄 인라인·검사 생략 지침은 그대로 채택하지 않았다.
- [Anti-Over-Engineering](https://github.com/PatrickJS/awesome-cursorrules/blob/main/rules/anti-overengineering.mdc): 실제 필요 없는 추상화·의존성·방어 코드 방지.
- [Simplicity First](https://github.com/farhadeidi/agents-md-guide/blob/main/patterns/simplicity-first.md): 현재 요구 중심의 단순함과 공개 계약·신뢰성 요구의 예외.

위 사례의 반복 의미를 design.md와 changes.md로 통합했다. 같은 문장을 강조 목적으로 중복 배치하지 않는다.

## 구조

- [graehl/agents](https://github.com/graehl/agents): 조건별 상세 문서 참조와 공통 원본 관리. 실제 글로벌 문서는 크고 자체 도구 의존성이 있어 구조 원리만 참고했다.
- [AGENTS.md Guide](https://github.com/farhadeidi/agents-md-guide): 원본을 한 곳에 두기, 조건이 있는 참조, 적용 경계·예외 명시. 프로젝트 전용 가이드이므로 초기화 절차와 프로젝트 한정 요구는 채택하지 않았다.
- [글로벌 Codex 템플릿](https://github.com/yuanguang-ai-lab/codex-global-agents-template): 글로벌·프로젝트 책임 구분. 상세 협업·위임 절차는 제외했다.

## 언어별 참고와 제외

- [Java/Spring 예제](https://github.com/PatrickJS/awesome-cursorrules/blob/main/rules/java-springboot-jpa-cursorrules-prompt-file.mdc): 언어·프레임워크 분류 참고. 특정 버전·JPA·Lombok 전제, Service/Impl 일괄 생성, Controller의 일괄 try-catch는 제외했다.
- [React/TypeScript 예제](https://github.com/PatrickJS/awesome-cursorrules/blob/main/rules/typescript-react-cursorrules-prompt-file.mdc): 컴포넌트·Hook·타입 주제 분류 참고. React.FC·고정 폴더 구조를 의무화하지 않았다.
- [스타일 일관성 예제](https://github.com/PatrickJS/awesome-cursorrules/blob/main/rules/code-style-consistency-cursorrules-prompt-file.mdc): 일관성 목적 참고. 매번 여러 파일 분석과 처리 없는 catch 후 재던지기는 제외했다.

## 공식 관례

- [JavaBeans 속성](https://docs.oracle.com/javase/tutorial/javabeans/writing/properties.html): get/set 및 primitive boolean의 is 접근자.
- [Java Record](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Record.html): record 접근자 예외. 특정 Java 버전 사용을 요구하지 않는다.
- [Google Java Style](https://google.github.io/styleguide/javaguide.html): Java 기본 스타일. 프로젝트의 명시적 규칙·설정과 이 세트의 변경 규칙을 우선한다. 일반 코딩은 로컬 지침으로 수행하고 미정의 세부 스타일의 판단이 필요할 때만 원문 해당 항목을 확인한다.
- [React 이벤트](https://react.dev/learn/responding-to-events): handle 내부 처리와 on 콜백 props 관례.
- [React useState](https://react.dev/reference/react/useState): 상태·setter 이름의 짝.
- [React 규칙](https://react.dev/reference/rules): 컴포넌트·Hook 사용 규칙.

get/fetch 구분, 구체적 함수 분리 조건, 참조·검증 범위는 이 세트가 선택한 기본값이다.
공식 규칙으로 포장하지 않고 실제 생성 결과를 검토하며 조정한다.

Java 기본 4칸 들여쓰기와 Java·React의 제어문 전후 빈 줄은 사용자가 선택한 정책이며 Google Java Style의 전체 준수를 뜻하지 않는다.

## 완성 시 보충한 근거와 선택

- [IoT DC3 지침](https://github.com/pnoker/iot-dc3/blob/main/AGENTS.md), [NetBird 지침](https://github.com/netbirdio/netbird/blob/main/AGENTS.md): 실행 가능한 설정을 기준으로 삼기, 환경 설정 중복 방지, 상태·분류의 타입 표현을 참고했다. 프로젝트 전용 계층·명명·설정 경로는 가져오지 않았다.
- [Spring Boot 외부 설정](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html): 외부 설정·프로파일과 ConfigurationProperties 바인딩 근거. 값의 변경 이유별 배치와 최소 상수 모듈은 이 세트의 선택이며 특정 Spring 버전을 요구하지 않는다.

- [ShardingSphere 구현 기준](https://github.com/apache/shardingsphere/blob/master/.codex/skills/code-implementation/references/rules/implementation.md), [실행·출력 효율](https://github.com/apache/shardingsphere/blob/master/.codex/context/token-efficiency.md): 사용자가 승인한 방어 코드 근거, 테스트 편의용 제품 구조 변경 제한, 기존 책임 소유자 중심 구현, 제한된 탐색·로그 출력 원칙을 기존 공통 규칙에 통합했다. 프로젝트 전용 도구·정식 리뷰·세밀한 승인 절차는 가져오지 않았다.

- [Apache ShardingSphere 테스트 기준](https://github.com/apache/shardingsphere/blob/master/.codex/skills/code-implementation/references/rules/testing.md): 실제 제품 동작·회귀 보호와 단순 접근자·전달·프레임워크·구현 구조·커버리지 목적 테스트 제한. 테스트 전용 삭제 규칙은 추가하지 않기로 사용자가 선택했다.
- [Apache ShardingSphere DB 실행 기준](https://github.com/apache/shardingsphere/blob/master/.codex/context/token-efficiency.md): 읽기 전용 조회와 DB 변경의 목적·영향·복구·명시적 승인. 이 세트는 직접·간접 DB 변경 실행 전 승인으로 명시했다.

- [불필요한 Effect 줄이기](https://react.dev/learn/you-might-not-need-an-effect): 파생 값은 렌더 중 계산하고 사용자 동작은 이벤트 경로에서 처리한다.
- [Custom Hook](https://react.dev/learn/reusing-logic-with-custom-hooks): UI의 React 로직을 의미 있는 Hook으로 묶되 일반 유틸리티와 구분한다.
- [Prettier 옵션](https://prettier.io/docs/options): React 형식 선택의 기준. 이 세트의 100자·작은따옴표는 자체 선택이며 Prettier 전체 기본값과 동일하다고 주장하지 않는다.
- [Spring 의존성 주입](https://docs.spring.io/spring-boot/reference/using/spring-beans-and-dependency-injection.html), [프록시](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): 생성자 주입과 인터페이스/클래스 프록시 지원. 프로젝트 버전에 맞게 적용한다.
- [Goal-Driven Verification](https://github.com/farhadeidi/agents-md-guide/blob/main/patterns/goal-driven-verification.md): 목적·위험에 비례한 검증.
- [ComfyUI 지침](https://github.com/Comfy-Org/ComfyUI/blob/master/AGENTS.md): 자명한 주석 억제와 구체적인 TODO. 함수마다 한국어 문서 주석과 주요 구간 설명은 사용자가 선택한 정책이다.

Java find/get/getRequired의 역할·값 없음 계약은 사용자가 선택한 정책이며 보편적 의무가 아니다. 필수 조건을 이름에 드러내는 참고 사례는 Spring Data의 [getRequiredSession](https://docs.spring.io/spring-data/cassandra/docs/3.4.2/api/org/springframework/data/cassandra/config/AbstractSessionConfiguration.html)이다. 파일 내부 선언 순서, 헤더 생략, TS 템플릿의 named export는 변경 가능한 추천값이다.
언어·프레임워크 규칙은 한 프로젝트의 업무·버전·폴더 구성에 의존하지 않는다. 이번 완성은 문서 구성·참조 검증까지이며 모델의 실제 준수율을 측정하지 않았다.

## 보안 초기 설정 통합 (2026-10-22)

사용자가 요청한 프로젝트 전반의 보안 초기 설정 지침과 공식 URL 목록을 docs/conventions/security/에 통합했다. OWASP ASVS는 애플리케이션 요구사항, Cheat Sheets는 구현, WSTG는 검증, CIS는 제품별 강화 기준으로 구분한다. CI/CD는 OWASP CI/CD Security, NIST SP 800-204D, NSA·CISA 지침을 공통 기준으로 사용한다. 공식 URL 원본은 ../docs/conventions/security/references.md에서 관리한다.

특정 프레임워크·클라우드·CI/CD 제품을 강제하지 않고 실제 기술과 버전에 맞게 선택한다. 기존 프로젝트의 일부 수정에 전체 초기화 절차를 강제하지 않는다. 공식 문서 갱신은 자동 적용을 뜻하지 않으며 적용 기준·확인일·검증·예외를 기록한다. 별도 security-setup-guide 폴더는 이 위치로 통합해 중복 원본을 남기지 않았다.
## 함수 설명과 부분 실패 기준 보완 (2026-10-22)

사용자가 ProcedureExportRunner 단일 파일 검토를 바탕으로 확정한 정책이다. 함수 설명은 중요한 데이터 출처·핵심 처리·결과와 부수 효과·실패 계약을 포함하고, 다른 함수에 위임한 핵심 동작도 설명한다. comments.md를 원본으로 하고 changes.md에서 실제 구현과의 대조를 요구한다.

데이터 보호는 design.md에서 단일 대상 손상 방지, 여러 대상의 부분 반영 허용 여부, 조회 결과 기반 삭제의 완전성을 구분한다. 임시 저장 후 교체는 가능한 구현 예시이며 모든 작업의 원자성·롤백·백업을 의무화하지 않는다. 이는 사용자와 합의한 개발 정책이며 특정 외부 표준의 직접 인용이 아니다.