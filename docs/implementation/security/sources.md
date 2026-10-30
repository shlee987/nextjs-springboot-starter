# 출처·확인 범위·갱신 기록

확인일: 2026-10-22. 공개 공식 페이지를 조회하고 아래 범위에서 요구사항을 요약했다. 공식 본문의 전문 번역·복제본이 아니다. 페이지의 latest 표시와 프로젝트 채택 버전을 구분한다.

## 실제 확인한 자료

| 자료 | 공식 URL | 확인 범위·설계 반영 |
| --- | --- | --- |
| OWASP ASVS | https://github.com/OWASP/ASVS/tree/v5.0.0/5.0/en | 공식 tag V1~V17 본문 17개·조항 345개 읽고 분류/설계 연결. asvs-mapping.md. 구현 충족 시험은 미실행 |
| WSTG 4.2 | https://wstg.owasp.org/v4.2/4-Web_Application_Security_Testing/ | SESS-05·ATHZ-04·BUSL-04 본문 확인, test-cases.md에 시험 연결. 전체 WSTG 매핑·실행은 아님 |
| OWASP HTTP Headers | https://cheatsheetseries.owasp.org/cheatsheets/HTTP_Headers_Cheat_Sheet.html | 헤더 종류·기능 호환·HSTS 경고·레거시 헤더 취급 → headers-csp |
| OWASP CSP | https://cheatsheetseries.owasp.org/cheatsheets/Content_Security_Policy_Cheat_Sheet.html | nonce/strict 정책·추가 방어 개념 → headers-csp |
| Next CSP | https://nextjs.org/docs/app/guides/content-security-policy | nonce 전달·동적 렌더링·정적 캐시 제약·dev/prod 차이 → headers-csp |
| Next Data Security | https://nextjs.org/docs/app/guides/data-security | HTTP API 접근·최소 DTO·서버/클라이언트 경계 → frontend |
| Next headers | https://nextjs.org/docs/app/api-reference/config/next-config-js/headers | 헤더 설정 경계·framework 계약 → headers-csp |
| Spring CSRF | https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html | unsafe method 보호·deferred/BREACH·인증 후 재발급 → backend |
| Spring CORS | https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html | preflight와 인증 필터 순서·허용 출처 → backend |
| Spring Security Headers | https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html | 기본 헤더·추가 설정과 실제 검증 → headers-csp |
| Boot Actuator | https://docs.spring.io/spring-boot/reference/actuator/endpoints.html | endpoint 노출·접근 통제 → infrastructure |
| OWASP Session | https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html | 쿠키·교체·만료·전달 경로·저장소 보호 → backend |
| OWASP Authentication | https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html | 비밀번호 길이·차단 목록·열거 방지·남용 통제 → backend |
| OWASP Password Storage | https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html | Argon2id 최소 설정·salt·비용 측정 → backend |
| OWASP Authorization | https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html | 기본 거부·매 요청 권한·리소스 경계 → backend |
| OWASP Input Validation | https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html | 필드·파싱 전 크기·문맥별 방어·추가 필드 → backend/frontend |
| OWASP REST Security | https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html | HTTPS·endpoint 통제·token 검증 관점 → backend |
| OWASP File Upload | https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html | 포맷·유형·저장·권한·자원 위험 → backend 조건부 |
| OWASP SSRF | https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html | 목적지 검증·redirect·네트워크 통제 → backend 조건부 |
| OWASP Logging | https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html | 보안 이벤트·로그 보호·수집 범위 → backend |
| OWASP Secrets | https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html | 중앙 관리·최소권한·생성/회전/폐기 → configuration |
| OWASP TLS | https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Security_Cheat_Sheet.html | 프로토콜·강한 cipher·인증서 → infrastructure |
| OWASP Docker | https://cheatsheetseries.owasp.org/cheatsheets/Docker_Security_Cheat_Sheet.html | socket·권한·rootfs·자원·이미지 공급망 → infrastructure 조건부 |
| OWASP Database | https://cheatsheetseries.owasp.org/cheatsheets/Database_Security_Cheat_Sheet.html | 전송·계정·최소권한·자격 보호 → infrastructure |
| OWASP CI/CD | https://cheatsheetseries.owasp.org/cheatsheets/CI_CD_Security_Cheat_Sheet.html | 저장소·실행 환경·IAM·의존성·무결성 → supply-chain |
| GitHub Actions Secure Use | https://docs.github.com/en/actions/reference/security/secure-use | 토큰 최소권한·PR/runner·시크릿·action 무결성 → supply-chain |
| NIST SP 800-204D (2024-02) | https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-204D.pdf | §5.1~5.2 본문: build attestation·source/권한·artifact 검증 → GOV14/V36. 전체 표준 준수 심사 아님 |

열람한 동적 문서에는 Next 16.4.2, Spring Security 7.1.1, Spring Boot 4.1.1 표시가 있었지만 프로젝트 버전을 선정하거나 호환성 검증한 것은 아니다. D01 이후 해당 버전 문서에 맞는 구체 API·설정 키·파일명 매핑을 완료해야 한다.

## 미선택 제품·추가 참조와 확인 한계

| 자료 | URL | 한계와 후속 작업 |
| --- | --- | --- |
| CIS Benchmarks | https://www.cisecurity.org/cis-benchmarks | 목록 진입점만. 실제 제품 benchmark 파일·판·profile 대조 안 함. 제품 확정 후 추가 |
| NSA/CISA CI/CD | https://media.defense.gov/2023/Jun/28/2003249466/-1/-1/0/CSI_DEFENDING_CI_CD_ENVIRONMENTS.PDF | 직접 원문 403, 색인된 일부 완화 본문만 확인. 전체 열람·준수 주장 금지. 현재 필수 통제의 유일 근거에서 제외 |
| IIS Security | https://learn.microsoft.com/en-us/iis/configuration/system.webserver/security/ | overview 열람, 일부 인증 안내 존재. 현행 배포 버전별 설정 검증 안 함 |
| Windows Server | https://learn.microsoft.com/en-us/windows-server/security/security-and-assurance | 보안 문서 모음만. OS 버전별 hardening 안 함 |
| SQL Server | https://learn.microsoft.com/en-us/sql/relational-databases/security/securing-sql-server | 개요 열람, 17.x 페이지로 연결. 채택·배포 검증 안 함 |
| Jenkins | https://www.jenkins.io/doc/book/security/ | 보안 장의 항목/개요만. 미선택 제품이며 설정 적용 안 함 |

자료 접근 실패: GitHub의 과거 security-hardening 경로는 도구에서 열리지 않아 현행 secure-use 경로로 대체 확인했다. 링크가 있다는 이유만으로 그 하위 문서·제품 전체를 확인했다고 보고하지 않는다.

## 갱신 규칙

설계자는 주요 버전·인증·렌더링·배포·신규 기능·취약점 권고·검증 불일치 발생 시 해당 공식 자료를 다시 확인한다. 일반 구현자는 유효한 프로젝트 설계를 참조하며 routine 작업마다 URL을 전부 열지 않는다.

갱신 시 이전 정책·변경 이유·영향 ID·새 버전·확인일·필요한 재검증을 기록한다. 예외가 생기거나 문서로 해소되지 않는 동작이면 구현자가 보안을 완화하지 않고 설계 이슈로 남긴다.

0.1 / 2026-10-22: 공통 지침과 공식 자료를 구현 요구사항·설정 계약·검증 관문으로 구체화. 모두 구현·배포 검증 전. 정책 수치·쿠키명·폴더·도구 후보는 설계자의 기본안으로 분리.

0.2 / 2026-10-22: ASVS 전체 ID 분류·설계 연결, 선택한 WSTG 본문 시험 연결, NIST §5.1~5.2 본문 검토. GOV01~GOV15·V23~V36 추가, 표준 차이/제품 관문 분리. [검토 결과](review-report.md) 참고. NSA/CISA 원문 접근 제한은 해소된 것으로 기록하지 않는다. 미선택 제품/CIS는 선택 후 버전별 검토 대상으로 관리한다.

## 구성·버전 후속 확인 — 2026-10-26

0.2 당시 제품 표시 버전은 당시 조사 기록이다. 2026-10-26 기본 구성 선정 결과는 [확정 버전표](../stack-versions.md)에 공식 릴리스·레지스트리·BOM·호환표 근거와 함께 관리한다. D01과 D05의 제품·버전 관문은 해소했다. 버전별 설정 키·실제 구현·CSP·세션·DB 동작 검증은 후속 작업이며 공통 보안 검토를 실행 통과로 변경하지 않는다.

## 백엔드 공통 기반 변경 확인 — 2026-10-29

JWT/공유 상태·외부 로그인 세션·quoted JDBC schema·날짜/숫자·MyBatis 매핑의 새 설계에 필요한 공식 관련 절을 열람했다. Spring Security7.1.2, Spring Session4.1.1, PostgreSQL18 문서를 확인했다. 전문 복제·신규 의존성 설치·호환 실행 검증은 하지 않았다. 프로젝트 정책과 출처의 일반 기능 설명을 구분한다.

| 출처 | 확인한 범위·반영 |
| --- | --- |
| https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html | JWT resource-server/JOSE·검증 설정, Nimbus 재사용 |
| https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html | 쿠키 저장소·토큰 표현/공급·상태변경 보호 |
| https://docs.spring.io/spring-session/reference/configuration/jdbc.html | schema/SQL 사용자화·JSON·정리·트랜잭션 경계 |
| https://docs.spring.io/spring-session/reference/configuration/redis.html | indexed/JSON/안전 직렬화·namespace |
| https://docs.spring.io/spring-security/reference/servlet/saml2/login/authentication-requests.html | HttpSession 요청 보관·콜백 복원 확장점 |
| https://mybatis.org/mybatis-3/configuration.html | mapUnderscoreToCamelCase·plugin·SQL whitespace 설정 |
| https://www.postgresql.org/docs/18/datatype-datetime.html | date/timestamptz 정밀도·저장과 표시 구분 |
| https://www.postgresql.org/docs/18/sql-syntax-lexical.html | quoted 대문자 식별자 |
| https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html | Argon2id·단방향 해시 원칙 |
| https://www.rfc-editor.org/rfc/rfc9700.html | refresh 회전·재사용 탐지, 자체 API를 OAuth 서버로 주장하지 않음 |

구체적 구조·수치·fence·테이블·API는 [최종 설계](../backend-foundation.md)와 연결 문서의 프로젝트 결정이다.


## 2026-10-30 공격 대응·파일 기능 보완

OWASP File Upload·XSS Prevention·SQL Injection Prevention Cheat Sheet와 Spring Security HttpFirewall 공식 문서를 확인했다. 적용 범위·선택한 제한·남은 바이러스 검사/운영 저장소 조건은 [공격 대응·파일 설계](../attack-defense-and-files.md)를 따른다. React 출력 처리를 이유로 서버 입력·파일·소유권 검사를 생략하지 않는다. 단일 필터/파일 시그니처/이미지 재인코딩을 모든 공격이나 악성코드 탐지 보장으로 설명하지 않는다.
