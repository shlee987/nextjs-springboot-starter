# 구현 설계 안내

문서 버전: 0.5 / 최초 작성: 2026-10-22 / 구성·버전 확인: 2026-10-26 / 상태: 1차 기반 구현·독립 리뷰·로컬 기초 연동 검증 완료. 개별 업무·인증·UI와 실제 배포 검증은 후속.

## 목적과 사용법

공통 지침을 이 프로젝트에서 실행할 작업·설정·검증으로 구체화한다. 일상 구현은 이 폴더와 연결된 로컬 공통 지침으로 진행한다. 공식 문서 본문을 복제한 오프라인 사본은 아니다.

1. [1차 프로젝트 세팅 범위](initial-setup.md), [결정·적용 범위](decisions.md), [확정 구성·버전표](stack-versions.md)를 읽어 현재 단계와 기본 조합을 확인한다.
2. [공통 API 응답](api-contract.md), [공통 구현 계획](engineering.md)과 담당 보안 문서를 읽는다.
3. 요구사항 ID에 구현 파일과 검증 증거를 연결한다.
4. [검증·완료 기준](security/verification.md)에 따라 결과를 기록한다.
5. [1차 변경 이력과 실제 검증 범위](change-history.md) 및 각 모듈 README로 현재 구현 상태를 확인한다.

| 담당 | 필수 설계 |
| --- | --- |
| 전체 | 이 문서, initial-setup.md, api-contract.md, decisions.md, engineering.md, security/baseline.md |
| 프론트엔드 | security/frontend.md, security/headers-csp.md, security/configuration.md |
| API·인증 | security/backend.md, security/headers-csp.md, security/configuration.md |
| 배포·DB | security/infrastructure.md, security/configuration.md, security/supply-chain.md |
| 검수 | security/verification.md, 담당 영역 설계 |
| 설계 유지보수 | security/sources.md, decisions.md, 요구사항별 변경 영향 |

## 문서 계약

- 공통 코딩 규칙의 원본은 [공통 진입점](../common-rules/AGENTS.md)이다. 여기서는 구현 산출물과 프로젝트 선택만 정의한다.
- 보안 기본 요구사항은 모든 환경에 적용한다. 로컬 예외는 항목별로 명시한 범위만 허용한다.
- “필수”는 완료 기준, “조건부”는 기능 도입 시 필수, “기본안”은 설계자가 제안한 정책이다. 기본안은 사용자 확정 사실과 구분한다.
- 미결정 항목은 작업자가 편의로 채우지 않는다. [1차 범위](initial-setup.md)에 포함한 기반은 진행하고 미정인 개별 기능만 해당 결정 시점까지 보류한다. 업무 기능 전체 설계가 프로젝트 세팅의 착수 조건은 아니다.
- “설계됨 / 구현됨 / 검증 통과”는 서로 다른 상태다. 기초 구현의 실제 통과 범위는 change-history.md와 모듈 README에 기록하며, 보안 요구사항 전체를 통과한 것으로 확대하지 않는다.
- 공식 문서 재확인은 신규 버전·새 제품·새 기능·보안 권고·문서와 실제 동작의 불일치가 있을 때 설계 담당자가 수행한다. 일상 작업마다 같은 페이지를 다시 읽을 필요는 없다.
- 공통 보안 기준 자료는 2026-10-22, 구성·버전 자료는 2026-10-26에 확인했다. 라이브러리 CVE·지원 종료·취약점 DB는 시간이 지나면 변하므로 릴리스 검사는 오프라인 문서로 대체하지 않는다.
- ASVS 5.0.0 V1~V17 본문 345개 조항의 분류·설계 연결을 완료했다. [조항 대조표](security/asvs-mapping.md)와 [보완 통제](security/controls.md)를 함께 적용한다. 실제 조항별 구현 시험·ASVS 인증·L2 전체 준수를 뜻하지 않는다.
- “취약점 없음” 대신 점검 범위·검사 버전·실행 시점·미해결 항목을 기록한다. 보안 헤더만으로 인증·권한·서버 취약점을 해결했다고 판단하지 않는다.

## 변경 범위

이 폴더는 공통 계약·설계·변경 이력을 관리한다. 구현은 frontend·backend·infra/local에서 관리하며 실제 수행한 검증과 미구현 기능을 구분한다. CI·운영 배포와 미정 업무 기능을 기본 설치하지 않는다. 조사 중간 파일·로그·개인 작업 자료는 저장소 밖의 지정 작업 공간에 둔다.

자료 근거: [출처와 열람 범위](security/sources.md). 기술적 권고와 프로젝트에서 선택한 수치·정책은 각 문서에서 구분한다.

## 공통 보안 검토 완료 기록

- [검토 결과와 다음 단계 경계](security/review-report.md)
- [ASVS 조항별 대조](security/asvs-mapping.md)
- [누락·충돌 보완 통제 GOV01~GOV15](security/controls.md)
- [상세 검증 시나리오 V23~V36](security/test-cases.md)

공통 기준 조사는 완료했다. 기본 기술·버전·로컬 DB 제품은 2026-10-26 확정했다. 이후에는 예제 기능·인증 provider·배포 제품과 해당 설정 키·실행 명령·구현 계약을 정한다. 출처의 전문 미열람/접근 제한과 미선택 제품은 sources에 명시한다.
## 기술 구성과 로컬 실행 환경

- [확정 기본 구성·정확 버전·선정 근거](stack-versions.md)
- [상황별 대체 조합·교체 영향](stack-alternatives.md)
- [전체 설계 독립 검토 결과·조치·한계](design-review-2026-10-26.md)
- [기본 라이브러리·역할·통합 원칙](technology-stack.md)
- [Docker 로컬 DB·통합 테스트 환경](local-environment.md)

각 문서는 사용자 확정 사항과 설계 제안을 구분한다. 실제 패키지 설치·Compose 작성·DB 실행을 의미하지 않는다.

## 인증 기반과 환경 독립성

- [인증 확장·Redis 공유 세션 기반](authentication-foundation.md)
- Docker는 로컬 DB/Redis 준비 도구이며, 다른 환경의 제품 실행·migration은 Docker 없이 가능하도록 설계한다.

## 현재 프로젝트의 데이터 접근 계약

- [MyBatis 역할·트랜잭션·연결·임시 테이블·검증](persistence.md)
- docs/common-rules는 모든 프로젝트의 범용 룰, 이 폴더는 현재 프로젝트의 공통 선택과 상세 계약을 관리한다. 특정 스택의 선택을 범용 룰에 반영하지 않는다.
