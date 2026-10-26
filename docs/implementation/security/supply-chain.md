# 공개 저장소·CI/CD·공급망 설계

CI 제품 기본안은 GitHub Actions이며 사용자 확정 사항과 구분한다. 다른 제품을 쓰면 동일 통제에 제품별 설정을 매핑한다.

## CI01 — 공개 저장소와 변경 권한

- main 직접 push·force push·삭제를 제한하고 PR·필수 검사 관문을 둔다. 개인 운영으로 독립 리뷰가 불가능하면 적용 가능한 보호와 그 한계를 예외 기록한다.
- workflow·보안 설정·의존성·설계 변경을 검토 대상으로 지정한다. CODEOWNERS의 존재만으로 리뷰 강제를 보장하지 않는다.
- 개인/관리자 계정 MFA, 자동화 계정 최소권한, 불필요한 토큰·deploy key 폐기. SSH 개인키나 PAT를 원격 URL·설정·문서에 포함하지 않는다.
- .gitignore·.dockerignore·artifact allowlist 적용, commit/PR에서 비밀값 검사. 로컬 hook만으로 필수 CI 관문을 대체하지 않는다.
- 이력 전체 비밀 탐지는 초기 기준선·유출 의심 시 수행한다. diff 검사만으로 과거 유출이 없다고 판단하지 않는다.

## CI02 — 신뢰하지 않는 입력과 실행 격리

| 경로 | 권한·격리 기준 |
| --- | --- |
| 외부 fork/PR | read-only 토큰, 시크릿·배포 환경·운영 네트워크 접근 없음 |
| 일반 검사 | 임시 runner, contents: read 기본. write 권한은 필요한 job에만 |
| 배포 | 검증된 신뢰 ref·환경·artifact만, 별도 자격·승인 관문 |
| 클라우드 접근 | 가능하면 OIDC 단기 자격, repo/ref/environment·audience 신뢰 조건 제한 |
| PR 메타데이터 | 제목·브랜치·body를 shell 코드에 직접 삽입 금지, 데이터 인자로 안전하게 전달 |
| 캐시·산출물 | PR과 배포 간 신뢰 경계 분리, 외부 PR 산출물을 운영에서 실행하지 않음 |
| 실행 환경 | 공개 PR에 장기 self-hosted runner·내부망 runner 사용 금지 기본안 |

pull_request_target·workflow_run 등 권한 상승 경로에서 외부 head checkout 후 실행하지 않는다. 토큰 persist·checkout 자격·artifact 다운로드·재사용 workflow도 같은 검토를 받는다. 서드파티 action은 전체 commit SHA 고정, 업데이트 시 출처·변경을 검토한다.

근거: [GitHub Secure Use](https://docs.github.com/en/actions/reference/security/secure-use). job별 설정과 이벤트 안전성은 실제 workflow 작성 후 정적 검증한다.

## CI03 — 의존성과 빌드

지원 버전·출처·라이선스·직접/전이 의존성을 목록화한다. frontend lockfile과 패키지 관리자가 자동 관리하는 integrity는 유지하고, backend는 버전/BOM·의존성 잠금·Wrapper 배포 checksum을 유지한다. 동적 latest·SNAPSHOT·범위 해석이 운영 재현성을 깨지 않게 한다.

사용자 결정: 기본 구성은 실제 취약점 대응과 안전한 기본 설정에 집중한다. Gradle 의존성별 수동 체크섬 등록, 패키지 버전별 설치 승인 및 게시 경과 시간 예외 목록처럼 일상 의존성 갱신마다 별도 수동 대장을 요구하는 강화 정책은 기본에서 제외한다. 필요 조직 정책이 있을 때 [문서 전용 선택사항](../stack-alternatives.md)으로 검토한다. 이를 ISMS의 특정 파일 의무나 누락 즉시 취약점으로 설명하지 않는다. lockfile 자동 integrity와 Wrapper checksum은 별개이며 유지한다. 알려진 취약점 확인·패치, 비밀 비노출, 인증·권한·CSRF·CSP·TLS 등 기존 대응 계약은 유지한다.

패키지 설치 스크립트·빌드 plugin·새 registry는 실행 코드로 취급한다. 무근거 전체 lifecycle 해제 대신 필요한 설치와 공급망 위험을 검토한다. 비밀 없는 빌드, 승인 registry, 오염된 캐시 격리를 구현한다.

배포 단위는 같은 commit의 검증된 artifact digest다. 환경별로 임의 재빌드한 것을 “검증한 동일 산출물”로 취급하지 않는다. SBOM과 source revision·빌드 도구 버전·digest를 보관하고, registry 및 배포 대상의 접근 권한을 제한한다. 서명·provenance는 플랫폼과 위험 수준에 맞게 확정하되 실제 검증 주체를 지정한다.

## CI04 — 필수 검사 단계

| 단계 | 검사·실패 조건 |
| --- | --- |
| PR | format/lint/typecheck/compile, 관련 단위·권한·검증 회귀, 비밀 탐지, SAST, SCA |
| workflow 변경 | workflow lint와 script injection·권한·pin 검토 |
| build | 생산 빌드, 번들 비밀 노출·금지 dev 설정, 필요한 SBOM |
| image/IaC 조건부 | image/IaC/misconfiguration 검사, 실제 자원 제한·권한 검토 |
| stg | 실제 프록시 포함 헤더·CSP·쿠키·권한·CSRF·기능, ZAP DAST |
| release | 열린 finding/예외 만료·증거·정확한 artifact·배포 자격 확인 |
| after deployment | health와 인증·권한·헤더 smoke, 실패 시 트래픽 중단/검증된 복구 절차 |

도구 후보는 CodeQL 또는 Semgrep(SAST), Gitleaks 등 비밀 탐지, Dependabot 및 선택 생태계 SCA, Trivy 등 image/IaC, ZAP DAST다. 후보는 설치·설정 완료를 의미하지 않는다. D01·D07 뒤 실제 도구 버전·명령·룰·검사 깊이·필수 check 이름을 고정한다. 실패 임계값은 [검증 기준](verification.md)으로 단일화한다.

## CI05 — 배포·로그·유지보수

동일 환경 동시 배포 잠금, 승인된 환경별 자격, 배포 이력·감사·롤백·migration 실패 정책을 명세한다. 저장소의 테스트 데이터 외 실제 운영 데이터를 CI에 주입하지 않는다. 로그·artifact·cache에 비밀·세션·덤프를 올리지 않고 공개 다운로드 권한을 점검한다.

주간 의존성 업데이트 확인, 매 릴리스 현재 advisory 검사 기본안. 알려진 실제 악용·원격 고위험 취약점은 발견 즉시 담당자가 차단/패치 계획을 정하고 해결 전 릴리스 차단한다. runner·OS·이미지·plugin·JDK도 업데이트 대상이다.

근거: [OWASP CI/CD](https://cheatsheetseries.owasp.org/cheatsheets/CI_CD_Security_Cheat_Sheet.html). [NIST SP 800-204D](https://csrc.nist.gov/pubs/sp/800/204/d/final)와 [NSA/CISA 안내](https://www.nsa.gov/Press-Room/Press-Releases-Statements/Press-Release-View/Article/3441780/nsa-and-cisa-best-practices-to-secure-cloud-continuous-integrationcontinuous-de/)는 공급망 기준의 보충 진입점이다. NIST는 0.2에서 §5.1~5.2 본문을 확인해 GOV14에 반영했다. NSA/CISA는 공식 PDF 직접 접근 403으로 전체 본문 확인 자료가 아니며 필수 항목의 유일 근거로 삼지 않는다. 두 자료 전체 준수 심사는 아니다. Jenkins는 미선택이고 별도 제품 보안 설정을 완료한 상태가 아니다.

## 본문 대조 보완 0.2

[controls](controls.md) GOV09의 위험별 패치 목표와 GOV14의 검증 주체·서명 증거·임시 자원 폐기 계약을 적용한다. 실제 구현 확인은 V31/V36이다. 서명 존재와 artifact/발급자/워크플로 검증을 구분하고 개인 운영의 독립 리뷰 예외를 기록한다.
