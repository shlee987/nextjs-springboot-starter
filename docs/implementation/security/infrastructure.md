# 서버·프록시·DB·운영 설계

이 문서는 제품 공통 실행 계약이다. D05·D06 해소 전 IIS/nginx/클라우드/DB별 실행 파일·레지스트리·명령을 추측해 작성하지 않는다. 제품 확정 뒤 본 계약의 설정 매핑표를 추가해야 운영 배포 준비가 완료된다.

## OPS01 — 네트워크와 신뢰

| 경로 | 허용 |
| --- | --- |
| Internet → 진입 서버 | HTTPS 443. HTTP 80은 브라우저의 안전한 페이지 탐색 전환·인증서 갱신만, /api 및 인증/변경은 거부 |
| 진입 서버 → Next | 지정 포트·출처만, 외부 직접 접근 차단 |
| 진입 서버 → Spring | /api 라우팅, 지정 포트·출처만 |
| Next → Spring | 필요한 고정 API 목적지만 |
| Spring → DB/세션 | 필요한 private endpoint·포트만 |
| 관리·모니터링 | VPN/전용 경로·인증·최소권한. 공용 DB·JMX·JDWP·Actuator 금지 |
| 외부 egress | 필요한 DNS·시간·인증·메일 등 승인 목적지만, 사용자 URL 기반 임의 통신 금지 |

localhost 개발 서버도 기본 루프백에 바인딩한다. VM·컨테이너의 바인딩과 호스트 published port를 별도로 검사한다.

## OPS02 — TLS·프록시

- 기본안 TLS 1.3 우선, 필요한 TLS 1.2만 허용. SSL/TLS 1.0/1.1 및 약한 cipher 비활성. 실제 cipher는 선택한 TLS 구현과 버전에 맞춰 고정한다.
- 신뢰 CA·정확한 hostname·인증서 체인을 검증하고 자동 갱신·만료 30/14/7일 경보 기본안. 검증 생략 코드는 local에도 기본 금지한다.
- 브라우저 페이지의 안전한 GET/HEAD만 APP_PUBLIC_ORIGIN으로 고정 HTTPS 전환한다. /api 및 인증/변경 HTTP 요청은 자동 redirect하지 않고 거부한다. 전달 Host에서 redirect 목적지를 임의 구성하지 않는다. 의도하지 않은 host는 거부하고 open redirect·host header poisoning을 검사한다.
- 신뢰 진입 프록시는 외부 Forwarded·X-Forwarded-*를 제거하고 실제 proto/host/client 정보로 다시 만든다. API는 네트워크로 검증한 프록시에서 받은 정보만 사용한다.
- edge부터 backend까지 암호화 여부를 기록한다. 운영의 내부·외부 서비스 홉은 TLS와 인증서 검증이 기본이다. 동일 호스트 루프백/격리 sidecar 평문을 선택하면 ASVS 12.3 요구와의 차이·경계·보완 통제를 D06에서 명시하며 L2 충족으로 체크하지 않는다.
- request framing이 불명확한 요청, 잘못된 Host·중복 충돌 길이 헤더는 진입점에서 거부한다. 프록시/서버 패치와 파서 계약을 일치시킨다.
- body·header·요청시간·연결수·동시성·응답 buffer를 제한하고 무한 대기·압축폭탄·slow request를 검증한다. 구체 크기·시간은 backend.md와 함께 매핑한다.
- 보안 헤더·CSP·쿠키·캐시는 [헤더 계약](headers-csp.md) 소유권을 따른다. 프록시 자체 오류에도 적용한다.

근거: [OWASP TLS](https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Security_Cheat_Sheet.html). 네트워크 표·알림 수치·제품별 결정 관문은 프로젝트 설계다.

## OPS03 — API·OS·관리면

Spring 운영 정책: 예외 메시지·binding 값·stacktrace를 응답에 포함하지 않음, 디버그 비활성, 기본 생성 로그인 계정 금지, 관리 endpoint 최소 노출. health는 단순 상태만 반환하고 env/configprops/heapdump/loggers 등 민감 기능을 외부에 공개하지 않는다. 별도 management port도 그 자체로 보호가 아니다.

운영 실행 계정은 관리자/root가 아닌 서비스 전용 계정, 실행 파일 읽기와 필요한 데이터 디렉터리 쓰기만 허용한다. 불필요한 패키지·서비스·기본 계정·목록 보기·샘플 페이지 제거. SSH/RDP는 관리 경로와 강한 인증, 관리자 MFA·최소권한·접속 감사. 시간 동기화·지원 버전·보안 패치·디스크 및 자원 경보를 운영 항목으로 둔다.

근거: [Spring Actuator](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html). OS별 강화 항목은 제품·버전 확정 후 CIS 또는 공급자 기준에 매핑한다.

## OPS04 — 컨테이너 조건부 요구

컨테이너 도입 시 non-root, read-only rootfs, 필요한 writable volume만, capability drop·no-new-privileges·기본 seccomp, CPU/메모리/PID 제한을 적용한다. privileged·host network·Docker socket mount·시크릿 build ARG/ENV 내장 금지.

최소 지원 base image를 digest로 고정하고 builder와 runtime을 분리한다. 빌드용 비밀은 secret mount 등 해당 도구의 안전한 전달 방식으로만 공급한다. 이미지 layer·history·context·SBOM·스캔 결과를 검증한다. 정적 분석을 위해 컨테이너 전체 보안 기능을 해제하지 않는다.
근거: [OWASP Docker Security](https://cheatsheetseries.owasp.org/cheatsheets/Docker_Security_Cheat_Sheet.html).

## OPS05 — DB·세션 저장소

- 애플리케이션 계정은 필요한 DML만, migration 계정은 필요한 DDL만, 관리 계정은 별도. runtime 앱에 DBA·슈퍼유저·스키마 변경 권한을 주지 않는다.
- 브라우저·Next에서 DB 접속하지 않는다. 데이터 계정·DB 네트워크와 관리 경로를 격리한다.
- 전송 TLS·인증서/호스트 검증, 저장·백업 암호화, 키 별도 관리, 계정·권한 감사. 세션 저장소도 같은 민감도 적용.
- 스키마 변경은 버전 있는 migration, 운영 시작 시 자동 파괴/생성·샘플 계정 주입 금지. 자동 실행 여부는 배포 절차에서 명시한다.
- 데이터 제약은 PK/FK/unique/not-null 등 실제 계약과 일치시킨다. SQL 바인딩과 안정적 페이징·수정 조건 검증은 API 설계를 따른다.
- 백업은 접근 제한·보관기간·별도 장애 영역·무결성·복구 시험을 포함한다. RPO/RTO·보존기간은 D10의 운영 배포 관문이다.
- 개인정보 수집·보존·삭제·비식별 기준을 정하고 운영 데이터가 preview/test로 복제되지 않게 한다.
- 문서 작성·코드 구현은 DB 변경 실행 승인이 아니다. AI는 직접·간접 변경 실행 전에 공통 database-access 규칙을 적용한다.

근거: [OWASP Database Security](https://cheatsheetseries.owasp.org/cheatsheets/Database_Security_Cheat_Sheet.html), [DB 실행 규칙](../../common-rules/docs/conventions/common/database-access.md).

## OPS06 — 운영 검증·사고 대응

운영 전 포트 노출·권한 거부·인증서 갱신·복구·시크릿 회전·로그 유출·리소스 고갈 시나리오를 실행한다. 공개 공격성 검사는 허가한 격리 대상에서만 수행한다. 경보 기본안: 반복 인증 실패, 5xx 급증, session store 장애, 만료 인증서, 백업 실패, 디스크/메모리 한도.

각 경보의 수신자·대응 시간·런북·롤백 기준을 확정한다. 취약 의존성 업데이트는 같은 테스트 관문을 거쳐 배포하고 이전 이미지·설정으로 복구할 수 있도록 보관한다. 취약한 과거 버전으로 무조건 롤백하는 절차는 금지한다.

## OPS07 — 제품별 상세화 관문

| 제품 | 채택 시 추가 산출물 | 지금 상태 |
| --- | --- | --- |
| IIS | 요청 필터·TLS·프록시·오류 헤더·app pool 권한·로그 설정 매핑 | 미선택, overview만 열람 |
| Windows Server | 지원 버전·방화벽·업데이트·서비스 계정·Defender·관리면 강화 | 미선택, 문서 진입점 열람 |
| SQL Server | TLS·DB 권한·감사·암호화·backup·계정 정책 | 미선택, 보안 개요 열람 |
| Linux/nginx/클라우드 | 실제 제품 버전별 네트워크·TLS·서비스·IAM·로그·강화 설정 | 미선택, 제품 문서 미검증 |
| CIS | 제품·버전·benchmark 판·profile·권고별 적용/예외 표 | 목록 확인만, benchmark 본문 대조 미완료 |

관련 공식 진입점과 열람 한계는 [출처 기록](sources.md). 위 관문이 남아 있는 동안 “서버 보안 설정 적용 완료”라고 보고하지 않는다.

## 공통 본문 대조 보완 0.2

[controls](controls.md) GOV06~GOV11/GOV14를 적용한다. 키·데이터·로그 대장, 중앙 감사 무결성, 개별·단기 서비스 신원, 파싱/메서드/.git 노출, pool·자원 한도, 공급망 증거 검증을 제품별 설정으로 매핑한다. 미선택 제품의 CIS·설정 문법은 선택 후 관문이며 일반 보안 조사 미완료와 구분한다.
