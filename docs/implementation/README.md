# 구현 설계 안내

문서 버전: 0.2 / 작성·자료 확인일: 2026-10-22 / 상태: 공통 보안 기준 조사·대조·정합성 검토 완료, 기술별 상세 명세·구현·배포 검증 전.

## 목적과 사용법

공통 지침을 이 프로젝트에서 실행할 작업·설정·검증으로 구체화한다. 일상 구현은 이 폴더와 연결된 로컬 공통 지침으로 진행한다. 공식 문서 본문을 복제한 오프라인 사본은 아니다.

1. [결정·적용 범위](decisions.md)를 읽어 현재 확정 범위와 구현 전제조건을 확인한다.
2. [공통 구현 계획](engineering.md)과 담당 보안 문서를 읽는다.
3. 요구사항 ID에 구현 파일과 검증 증거를 연결한다.
4. [검증·완료 기준](security/verification.md)에 따라 결과를 기록한다.

| 담당 | 필수 설계 |
| --- | --- |
| 전체 | 이 문서, decisions.md, engineering.md, security/baseline.md |
| 프론트엔드 | security/frontend.md, security/headers-csp.md, security/configuration.md |
| API·인증 | security/backend.md, security/headers-csp.md, security/configuration.md |
| 배포·DB | security/infrastructure.md, security/configuration.md, security/supply-chain.md |
| 검수 | security/verification.md, 담당 영역 설계 |
| 설계 유지보수 | security/sources.md, decisions.md, 요구사항별 변경 영향 |

## 문서 계약

- 공통 코딩 규칙의 원본은 [공통 진입점](../common-rules/AGENTS.md)이다. 여기서는 구현 산출물과 프로젝트 선택만 정의한다.
- 보안 기본 요구사항은 모든 환경에 적용한다. 로컬 예외는 항목별로 명시한 범위만 허용한다.
- “필수”는 완료 기준, “조건부”는 기능 도입 시 필수, “기본안”은 설계자가 제안한 정책이다. 기본안은 사용자 확정 사실과 구분한다.
- 미결정 항목은 작업자가 편의로 채우지 않는다. 영향을 받는 작업은 decisions.md에 정한 시점까지 보류하고 결정 후 문서를 먼저 갱신한다.
- “설계됨 / 구현됨 / 검증 통과”는 서로 다른 상태다. 지금 모든 보안 항목은 설계 단계이며 검증 통과 항목은 없다.
- 공식 문서 재확인은 신규 버전·새 제품·새 기능·보안 권고·문서와 실제 동작의 불일치가 있을 때 설계 담당자가 수행한다. 일상 작업마다 같은 페이지를 다시 읽을 필요는 없다.
- 웹 자료 확인은 2026-10-22 시점이다. 라이브러리 CVE·지원 종료·취약점 DB는 시간이 지나면 변하므로 릴리스 검사는 오프라인 문서로 대체하지 않는다.
- ASVS 5.0.0 V1~V17 본문 345개 조항의 분류·설계 연결을 완료했다. [조항 대조표](security/asvs-mapping.md)와 [보완 통제](security/controls.md)를 함께 적용한다. 실제 조항별 구현 시험·ASVS 인증·L2 전체 준수를 뜻하지 않는다.
- “취약점 없음” 대신 점검 범위·검사 버전·실행 시점·미해결 항목을 기록한다. 보안 헤더만으로 인증·권한·서버 취약점을 해결했다고 판단하지 않는다.

## 변경 범위

이번 산출물은 설계문서다. 애플리케이션 소스, CI, 서버 설정을 적용하거나 DB를 실행하지 않는다. 조사 중간 파일·로그·개인 작업 자료는 저장소 밖의 지정 작업 공간에 둔다.

자료 근거: [출처와 열람 범위](security/sources.md). 기술적 권고와 프로젝트에서 선택한 수치·정책은 각 문서에서 구분한다.

## 공통 보안 검토 완료 기록

- [검토 결과와 다음 단계 경계](security/review-report.md)
- [ASVS 조항별 대조](security/asvs-mapping.md)
- [누락·충돌 보완 통제 GOV01~GOV15](security/controls.md)
- [상세 검증 시나리오 V23~V36](security/test-cases.md)

공통 기준 조사는 완료했다. 이후에는 버전·인증·DB·배포 제품을 정하고 해당 설정 키·실행 명령·구현 계약을 확정한다. 출처의 전문 미열람/접근 제한과 미선택 제품은 sources에 명시한다.
