# 공식 보안 참조 문서

목록 정리일: 2026-10-22. URL은 공식 문서의 진입점이며, 적용 시점에 최신 내용과 대상 버전을 다시 확인한다. 버전이 고정된 표준은 개정판 여부도 확인한다.

## 공통 기준 — 기술과 무관하게 참조

| 문서 | URL | 역할 |
| --- | --- | --- |
| OWASP ASVS | https://asvs.owasp.org/ | 인증·권한·입력·API·암호화·설정·로그 등 애플리케이션 보안 요구사항. 위험도에 맞는 검증 수준을 선택한다. |
| OWASP Cheat Sheet Series | https://cheatsheetseries.owasp.org/ | XSS·CSRF·CSP·세션·파일 업로드·비밀값·키 관리·DB 등 구현 지침. |
| OWASP WSTG | https://owasp.org/projects/web-security-testing-guide | 보안 통제 검증과 수동·자동 테스트 설계. |
| CIS Benchmarks | https://www.cisecurity.org/cis-benchmarks | 운영체제·웹 서버·DB·클라우드·컨테이너 등 제품과 버전에 맞는 강화 설정. |

ASVS 하나만으로 OS·클라우드·배포 설정 전체를 대신하지 않는다. Cheat Sheets와 제품별 공식 문서, 인프라 기준을 함께 사용한다.

## CI/CD — 도구에 독립적인 공통 기준

| 문서 | URL | 역할 |
| --- | --- | --- |
| OWASP CI/CD Security Cheat Sheet | https://cheatsheetseries.owasp.org/cheatsheets/CI_CD_Security_Cheat_Sheet.html | 저장소·파이프라인 권한, 비밀값, 의존성, 실행 환경, 산출물 무결성, 로그 등 실무 기본 기준. |
| NIST SP 800-204D | https://csrc.nist.gov/pubs/sp/800/204/d/final | 소스·빌드·패키징·배포 과정에 소프트웨어 공급망 보안을 통합하는 기준. |
| NSA·CISA CI/CD 보안 가이드 | https://www.nsa.gov/Press-Room/Press-Releases-Statements/Press-Release-View/Article/3441780/nsa-and-cisa-best-practices-to-secure-cloud-continuous-integrationcontinuous-de/ | 클라우드 CI/CD 환경의 접근 통제와 실행 환경 보호. 공식 안내 페이지에서 가이드 원문을 확인한다. |

Azure·AWS는 배포 대상이면서 CI/CD 서비스 제공자일 수도 있다. 배포 대상의 IAM·네트워크·키 관리와 파이프라인 자체의 권한·실행 환경을 각각 검토한다. 실제 도구를 정한 뒤 해당 제품의 공식 문서로 설정 방법을 보충한다.

## 기술별 보충 문서 — 해당 기술 사용 시에만 참조

아래는 현재 논의한 기술의 예시 목록이며 필수 스택이 아니다. 다른 기술을 선택하면 같은 역할의 공식 보안 문서를 찾아 추가한다.

| 대상 | 공식 URL | 확인할 내용 |
| --- | --- | --- |
| Next.js CSP | https://nextjs.org/docs/app/guides/content-security-policy | nonce·hash 등 CSP 구현과 렌더링·캐시·외부 라이브러리 호환성. |
| Next.js Data Security | https://nextjs.org/docs/app/guides/data-security | 서버·클라이언트 경계, 데이터 전달, 비밀값 노출 방지. |
| Spring Security | https://docs.spring.io/spring-security/reference/ | 인증·권한·세션·CSRF·CORS·보안 헤더. |
| IIS Security | https://learn.microsoft.com/en-us/iis/configuration/system.webserver/security/ | 인증·접근 제어·요청 필터링·SSL 관련 구성. |
| Windows Server Security | https://learn.microsoft.com/en-us/windows-server/security/security-and-assurance | 운영체제 보안 기능과 서버 보호. |
| SQL Server Security | https://learn.microsoft.com/en-us/sql/relational-databases/security/securing-sql-server | 계정·권한·접속 보호·DB 보안. |
| Jenkins Security | https://www.jenkins.io/doc/book/security/ | Jenkins를 선택한 경우에만 Credentials·사용자 권한·에이전트·플러그인·빌드 환경 설정을 보충. |

## 관리 방법

프로젝트별 적용 기록은 `대상 / 공식 URL / 적용한 기준·제품 버전 / 확인일 / 설정 위치 / 검증·예외` 정도로 관리한다. 공식 문서 본문을 복사해 고정하지 않고 URL과 실제 적용 결정을 남긴다. 링크 이동·개정 시 공식 사이트에서 후속 문서를 찾아 갱신하며 출처가 불분명한 요약을 기준으로 대체하지 않는다.