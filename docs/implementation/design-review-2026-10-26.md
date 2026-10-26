# 전체 설계 독립 검토 결과

검토일: 2026-10-26 / 대상: 확정 구성·버전·대체 조합과 전체 프로젝트 설계문서 / 방식: 보조 에이전트 1개의 독립 검토 + 설계 담당자의 대조·수정.

이 문서는 구현 전 설계 검토 당시의 기록이다. 이후 사용자 결정과 실제 구현·검증 상태는 [1차 변경 이력](change-history.md), 최신 버전표와 모듈 README를 우선한다.

## 결론과 범위

공식 배포 메타데이터와 호환 범위에서 기본 버전표를 변경할 충돌은 발견하지 않았다. 문서 간 충돌 4건을 발견해 아래와 같이 보완했다. PostgreSQL 임시 테이블 권한도 추가로 명확히 했다. 이번 결과는 문서·공식 근거 검토이며 애플리케이션 실행 호환성이나 보안 통과 판정이 아니다.

## 발견 사항과 조치

| ID | 중요도 | 발견 | 수정 위치·조치 |
| --- | --- | --- | --- |
| REV01 | P1 | AG Grid 기본 테마의 SVG data 아이콘과 기존 img-src self 정책 충돌 | [헤더 HDR03·HDR07](security/headers-csp.md): img-src에만 data: 한정 허용, styleNonce 연결·문자열식 대신 함수·외부 폰트 미사용을 기록 |
| REV02 | P2 | React style prop을 CSP가 항상 차단한다고 일괄 설명 | [HDR07](security/headers-csp.md): SSR HTML·setAttribute/cssText와 CSR CSSOM 속성 할당을 구분. Radix·AG Grid·ECharts 전체 동작의 실제 CSP 시험 관문 유지 |
| REV03 | P2 | 확정 DB·MyBatis·빌드 선택을 일부 문서에서 미정으로 표현 | [구현 계획](engineering.md), [데이터 접근](persistence.md), [로컬 준비](local-environment.md), [운영 계약](security/infrastructure.md): 제품 선정 완료와 후속 상세·실행 검증을 분리 |
| REV04 | P2 | JSON 로깅·중앙 수집기·APM이 기본 구성인지 문서 전용 대안인지 불일치 | [GOV08](security/controls.md), [기본 버전표](stack-versions.md), [역할](technology-stack.md), [대안](stack-alternatives.md): 기본 Boot 로깅·운영 JSON 출력과 환경별 중앙 수집/APM 연결을 분리 |
| REV05 | 보완 | 앱 DML 최소권한과 임시 테이블 작업의 관계가 불명확할 수 있음 | [데이터 접근](persistence.md), [로컬 준비](local-environment.md), [OPS05](security/infrastructure.md): PostgreSQL TEMPORARY 권한과 영구 schema DDL을 구분하고 ON COMMIT DROP 방향·pool 재사용 검증 명시 |

CSP의 이미지 허용은 script·font·connect·object에 대한 data: 허용이 아니다. ECharts canvas/richText 경로도 전체 CSP 호환성을 증명하지 않으므로 실제 컨테이너·canvas·tooltip 출력을 구현 시 검증한다. API·공통 컨트롤 상세 설계는 이번 검토 범위에서 선구현하지 않았다.

## 수행한 확인

- npm 정확 버전의 게시 여부와 최신 안정 태그, Node/React peer·engine 조건 확인.
- TypeScript 6·ESLint 9의 의도적 선택이 Next lint 의존성 지원 범위에 맞는지 확인.
- Boot 4.1.2 BOM, Spring Data BOM, MyBatis starter 4.1.0 관리 값과 버전표 대조.
- 최소 Boot Test 모듈에 AssertJ·Mockito가 강제로 추가되지 않는 구조 확인. springdoc의 Actuator 연동이 optional임을 확인.
- Microsoft OpenJDK 25.0.5.0.2 Windows/Linux, Gradle 9.8.2, PostgreSQL 18.6, Redis 8.10.2 공식 배포 근거 확인.
- ASVS 대조표 345개 고유 ID와 L1 70·L2 183·L3 92 집계 일치 확인.
- 전체 Markdown 상대 파일 링크 검사와 프로젝트 문서의 형식·diff 검사.
- 저장소 텍스트에서 자격 토큰·개인키·개인 PC 절대 경로·개인/회사 이메일 표식 검색. 검색한 패턴의 실제 민감정보 미발견.
- 범용 공통 지침은 원본과 동일한 33개 파일을 보존. 이 검토의 수정은 프로젝트별 문서에만 반영.

버전 선정 근거는 [버전표](stack-versions.md), 스타일 출력과 AG Grid 보안 근거는 [HDR07](security/headers-csp.md)에 연결했다. Boot 기본 구조화 로그 근거는 [공식 로깅 문서](https://docs.spring.io/spring-boot/reference/features/logging.html#features.logging.structured)다.

## 미실행·후속 관문

- 의존성 설치·lockfile 생성·Next/Java 빌드·Orval 생성 결과 타입 검사 미실행.
- 브라우저 UI/CSP·공유 세션·직렬화·트랜잭션·임시 테이블·migration·Docker 없는 이식 시험 미실행.
- 운영 배포 대상·IdP·예제 기능·컨트롤 목록·상세 옵션은 후속 설계에서 결정.
- Git 이력 전체 비밀정보 검사와 전문 secret scanner 실행은 하지 않았다. 현재 텍스트 패턴 검사만으로 모든 형태의 비밀값 부재를 보장하지 않는다.
- 현재 .gitignore는 비어 있다. 아직 문서만 있으므로 유출 증거는 아니지만, 소스·로컬 자격·로그·생성 산출물을 만들기 전에 CFG02에 맞는 제외 규칙을 먼저 적용해야 한다.
- 공통 지침 원본 4개 파일의 기존 끝 개행 부재는 내용과 무관한 형식 사항으로 보존했다. 프로젝트별 검토를 이유로 범용 원본·사본의 형식을 임의 변경하지 않았다.

검토 후 문서 상태를 실행 검증 통과로 승격하지 않는다. 실제 구현 단계에서 관련 요구사항 ID에 설정 위치와 검증 증거를 연결해야 한다.


## 수정본 독립 재검토

동일한 보조 에이전트가 수정본을 재검토하여 REV01~REV04 모두 문서 수준에서 해소됐음을 확인했고 추가 수정이 필요한 새 문제는 발견하지 않았다. REV05의 PostgreSQL 권한·임시 테이블 보완도 확인했다. 최종 전체 Markdown 52개 상대 파일 링크 오류 0건, 프로젝트 문서 형식 검사와 diff 검사 통과, ASVS 345개 고유 ID 집계 일치, 공통 지침 33개 원본·사본 해시 불일치 0건이다. 실행 검증의 미실행 상태는 유지한다.
