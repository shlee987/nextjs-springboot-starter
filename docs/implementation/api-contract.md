# 공통 API 응답 계약

기준일: 2026-10-30 / 적용: 공통 기반 구현과 후속 파일 API에 적용한다. 실제 명세는 docs/api/openapi.json, 검증 상태는 foundation-status.md와 모듈 검증표를 따른다. 과거 1차 기록과 현재 결과를 구분한다.

목표는 백엔드 개발자가 공통 반환 함수를 사용하고 프론트가 정해진 양식으로 처리하게 하는 것이다. 개별 업무 URL·로그인 payload·도메인 DTO는 기능별 단계에서 정한다. 이 문서는 현재 프로젝트의 공통 계약이며 범용 공통 지침에 복사하지 않는다.

## 1. HTTP와 JSON 원칙

- 업무 JSON API의 성공은 ApiResponse<T>, 실패는 RFC 9457 Problem Details 계열의 아래 고정 계약을 사용한다. 성공·실패 모두 임의 Map이나 개별 래퍼를 만들지 않는다.
- HTTP 상태가 결과를 나타낸다. 오류를 HTTP 200과 success=false로 숨기지 않는다. 프론트도 HTTP 상태를 기준으로 성공/오류를 분리한다.
- 성공 Content-Type은 application/json, 오류는 application/problem+json이다. 본문이 있는 응답만 JSON을 직렬화한다.
- 같은 요청의 서버 생성 traceId를 응답 본문·X-Request-ID 헤더·정제 로그에서 동일하게 사용한다. UUID v4 문자열을 기본으로 하고 클라이언트 제공 ID를 그대로 채택하지 않는다. MDC를 사용하면 요청 종료 때 정리한다. 별도 APM·분산 추적 도구 기본 도입을 뜻하지 않는다.
- 원문 비밀번호·세션·토큰·SQL·스택·내부 경로는 반환하지 않는다. traceId는 진단 식별자이며 인증 정보가 아니다.
- Location·Allow·Retry-After·인증 challenge·Set-Cookie 등 해당 HTTP/인증 계약의 헤더는 공통 반환·오류 처리에서 보존한다.
- 204·HEAD에는 본문을 만들지 않는다. 향후 파일/stream·redirect·외부 인증 callback은 해당 프로토콜 계약을 따르고 JSON으로 자동 감싸지 않는다.

## 2. 성공 응답

고정 최상위 필드는 data와 traceId다. 타입별 업무 필드는 data 안에만 둔다.

    {
      "data": { "id": "sample-id" },
      "traceId": "550e8400-e29b-41d4-a716-446655440000"
    }

- 단일 결과: data는 해당 응답 DTO. Java entity·Mapper 내부 모델을 그대로 노출하지 않는다.
- 목록: data는 배열. 빈 결과는 빈 배열이며 null이 아니다.
- 결과가 없는 작업: 명시한 204를 우선 사용한다. 해당 API가 200을 선택했다면 data:null을 사용하고 임의 빈 객체와 혼용하지 않는다.
- 생성: 201과 응답 DTO를 사용하며 자원 URI가 명확하면 Location을 제공한다. URI는 서버의 승인 경로로 구성한다.
- success·HTTP status 복사 필드·기본 성공 문구는 추가하지 않는다. 화면의 성공 알림은 업무 흐름이 필요할 때 프론트 메시지에서 관리한다.

## 3. 페이지 응답

페이지를 실제 도입할 때 사용할 공통 모델 PageData<T>를 1차에 정의한다. 페이지 조회 기능과 SQL은 후속 단계다.

    {
      "data": {
        "items": [],
        "page": 0,
        "size": 20,
        "totalElements": 0,
        "totalPages": 0
      },
      "traceId": "550e8400-e29b-41d4-a716-446655440000"
    }

- page는 0부터 시작한다. size 기본 20, 최대 100이며 0·음수·상한 초과는 400으로 거부한다. 자동으로 무제한 조회하지 않는다.
- items는 항상 배열이다. totalElements는 같은 검색·권한 조건의 전체 건수, totalPages는 올림 계산하며 전체 0건이면 0이다.
- 프론트 그리드가 1부터 표시하면 공통 연결 경계에서 변환한다. backend와 generated client의 page 의미를 바꾸지 않는다.
- cursor·무한 스크롤·전체 건수 없는 slice는 해당 기능에서 별도 명세한다. 1차에 미사용 모델을 추가하지 않는다.

## 4. 오류 응답

필수 필드는 type·title·status·code·traceId, 선택 필드는 fieldErrors다.

    {
      "type": "about:blank",
      "title": "Bad Request",
      "status": 400,
      "code": "VALIDATION_FAILED",
      "traceId": "550e8400-e29b-41d4-a716-446655440000",
      "fieldErrors": [
        { "field": "name", "code": "REQUIRED" }
      ]
    }

- type은 초기에는 about:blank, title은 안전한 해당 HTTP 상태 제목이다. 업무 오류 구분은 고정 code가 맡는다. 실제 문서 URI로 바꾸는 경우 별도 계약을 갱신한다.
- status는 실제 HTTP 상태와 같아야 한다. fieldErrors가 없으면 생략한다.
- fieldErrors의 field는 해당 API의 공개 요청 DTO 필드 경로, code는 허용된 검증 식별자다. 같은 필드·코드 중복은 제거한다. 알려지지 않은 내부 경로를 자동 노출하지 않는다.
- fieldErrors에 message·rejectedValue·원문 예외를 넣지 않는다. 프론트는 허용 code/field를 공통 한국어 문구와 입력란에 매핑한다. 미등록 코드는 일반 오류로 표시한다.
- detail·instance에 프레임워크 기본 예외 문구나 실제 요청 경로가 자동 포함되지 않게 공통 출력 필드를 제한한다. Spring 기본 오류 응답도 이 계약으로 정규화한다.
- Security의 AuthenticationEntryPoint·AccessDeniedHandler, MVC ControllerAdvice, 프레임워크의 API 오류가 동일한 생성 경계를 사용한다. 프록시 자체 오류는 HTTP 상태를 유지하고 프론트가 안전한 일반 오류로 처리한다.

| 초기 공통 code | HTTP | 의미 |
| --- | --- | --- |
| VALIDATION_FAILED | 400 | 입력 제약 위반 |
| MALFORMED_REQUEST | 400 | JSON·타입·요청 형식 오류 |
| UNAUTHENTICATED | 401 | 인증 필요 |
| ACCESS_DENIED | 403 | 권한 부족 |
| CSRF_TOKEN_MISSING / CSRF_TOKEN_INVALID | 403 | CSRF 누락 / 유효하지 않음 |
| NOT_FOUND | 404 | 공개 가능한 자원·경로 없음 |
| METHOD_NOT_ALLOWED | 405 | 지원하지 않는 HTTP 메서드 |
| CONFLICT | 409 | 업무 상태·동시성 충돌 |
| PAYLOAD_TOO_LARGE | 413 | 요청 크기 상한 초과 |
| UNSUPPORTED_MEDIA_TYPE | 415 | 미지원 요청 Content-Type |
| RATE_LIMITED | 429 | 요청 한도 초과 |
| INTERNAL_ERROR | 500 | 안전하게 정제한 내부 오류 |
| DEPENDENCY_UNAVAILABLE | 503 | 필수 외부 서비스 사용 불가 |

권한상 존재 은닉이 필요한 API는 기능 계약에 따라 NOT_FOUND를 일관되게 사용할 수 있다. 업무별 코드는 기능 설계 때 추가하며 기존 코드 의미를 재사용 목적으로 변경하지 않는다. 초기 필드 검증 식별자는 REQUIRED·INVALID_FORMAT·OUT_OF_RANGE·INVALID_LENGTH를 제공하고 검사 규칙과 매핑한다.

## 5. 구현자가 제공할 공통 도구

| 구성 | 1차 책임 |
| --- | --- |
| ApiResponse<T> | 성공 모델. data·traceId 필드 고정 |
| PageData<T> | 페이지 모델. 비즈니스 검색 로직 미포함 |
| ApiResponses | ok(data)·created(location,data)·noContent() 반환 편의 함수. HTTP 상태·traceId·헤더를 공통 적용 |
| 공통 오류 정의·업무 예외 | code·HTTP·안전한 title 및 메시지 키를 한 곳에서 연결. 업무별 catch/응답 조립 방지 |
| 공통 오류 생성 경계 | 표준 ProblemDetail 지원을 활용하되 허용 출력 필드를 고정하고 JSON·필터·MVC 오류를 동일하게 처리 |
| 프론트 API 모델·요청 경계 | Orval 생성 결과와 공통 Axios를 연결. 응답 정규화·CSRF·취소·안전한 오류 처리 |

사용 목표는 Controller에서 return ApiResponses.ok(result); 정도로 반환하는 것이다. 업무 서비스는 HTTP 응답 모델을 반환하지 않는다. 상태·traceId·오류 JSON을 각 Controller가 반복 작성하지 않는다.

모든 응답을 ResponseBodyAdvice로 무조건 감싸는 방식은 기본 사용하지 않는다. 이중 래핑과 String·ProblemDetail·204·파일 응답 손상을 막기 위해 명시적 공통 반환 함수를 사용한다. 오류는 공통 처리 경계가 맡는다.

## 6. 프론트·OpenAPI 연결

- Spring Controller의 응답 타입에 실제 구체 DTO를 반영해 springdoc이 ApiResponse<구체 DTO>, PageData<구체 DTO>를 표현하게 한다. Object·무정형 Map·와일드카드만으로 명세하지 않는다.
- OpenAPI paths는 /api 접두사를 포함한다. 생성 경로를 다시 잘라내지 않는다. 브라우저 공통 Axios baseURL은 /로 고정해 /api가 두 번 붙지 않게 한다.
- 공통 mutator는 AxiosResponse를 반환하지 않고 성공 JSON의 ApiResponse<T> 본문을 반환한다. TanStack Query의 query 데이터는 이 envelope다. 화면 데이터 접근은 query.data?.data가 기준이며 호출마다 자동 unwrap 정책을 달리하지 않는다.
- 오류는 HTTP 상태와 허용된 code·traceId·fieldErrors로 정규화한다. non-JSON 프록시 오류·네트워크 오류도 예외 원문 없이 일반 실패로 처리한다.
- 1차에는 공통 모델과 생성 설정만 준비할 수 있다. 업무 endpoint·생성 대상 spec이 아직 없으면 없는 API를 만들거나 성공한 생성 검사로 보고하지 않는다.
- 기본 생성기는 Orval 하나이며 실제 API가 추가되는 단계부터 spec export → 생성 → 타입 검사 순서로 연결한다.

## 7. 1차 확인 범위

성공 모델·빈 결과·페이지 모델·공통 오류·실제 HTTP 상태·traceId 일치·204 본문 없음·민감값 미노출을 기본 JUnit과 필요한 Spring 테스트로 확인한다. 정해지지 않은 업무 API·회원 DB·로그인 시나리오를 만들어 시험하지 않는다. 추가 테스트 도구는 기본 설치하지 않는다.

공식 근거: [Spring 오류 응답·ProblemDetail](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html). 성공 envelope·편의 함수·페이지/오류 코드·Orval 반환 규칙은 이 프로젝트의 선택이다. [보안 API 계약](security/backend.md), [1차 세팅](initial-setup.md)을 함께 적용한다.

## 8. 실제 예외 정보 비노출 — 1차 필수

이 기준은 local·dev·stg·prod 모두에 적용한다. 개발 편의를 위해 API 응답에 원문 예외를 노출하는 별도 모드는 두지 않는다. 디버깅은 서버 로그·디버거와 traceId로 수행한다.

### 분류와 공통 처리

| 분류 | API 반환 | 처리 경계 |
| --- | --- | --- |
| 승인된 업무 오류 | 등록된 HTTP 상태·code·안전한 title | 공통 업무 예외/코드 정의 → ControllerAdvice |
| 입력·파싱·메서드·미디어 오류 | 400·405·413·415 등 계약한 상태와 고정 코드 | Spring MVC 예외 처리 |
| 인증·권한·CSRF 오류 | 401/403과 공통 코드 | AuthenticationEntryPoint·AccessDeniedHandler·CSRF 처리 |
| 승인한 의존 서비스 장애 | 등록된 경우 503·DEPENDENCY_UNAVAILABLE | 외부 실패를 정규화하는 공통 경계 |
| 미분류 예외 | 500·INTERNAL_ERROR·Internal Server Error | 최종 Exception 처리 경계 |

업무 예외도 getMessage()가 곧 공개 문구인 것은 아니다. 미등록 코드·개발자가 임의 전달한 문구·라이브러리 예외는 안전한 공통 실패로 처리한다. 이미 분류된 4xx를 무조건 500으로 바꾸지 않는다.

### 공개 필드와 금지 경로

- 응답은 4절의 허용 필드에서 새로 구성한다. Exception·cause·BindingResult·DB 오류 객체·외부 HTTP 응답·임의 ProblemDetail properties를 그대로 직렬화하지 않는다.
- getMessage()·getLocalizedMessage()·toString()·printStackTrace()의 결과를 body·헤더·HTML에 넣지 않는다. 중첩 cause와 suppressed 예외의 메시지도 같은 규칙을 적용한다.
- exception·trace·stackTrace·cause·message·detail·instance·path·errors 등의 프레임워크 자동 출력 필드를 통과시키지 않는다. 공개 입력 오류는 fieldErrors의 허용 field/code만 사용한다.
- 테이블/컬럼명·제약조건명·SQL/바인딩 값·클래스/패키지명·로컬 파일 경로·DB 주소·외부 서비스 오류 본문이 title·code·fieldErrors·헤더로 유입되지 않아야 한다.
- @RestControllerAdvice만으로 모든 오류가 처리된다고 가정하지 않는다. Security 필터, 그 앞뒤의 앱 필터/세션 저장 오류, Servlet error dispatch와 /error fallback도 같은 공통 오류 생성 경계를 사용한다. /error를 직접 호출해도 기본 오류 정보·HTML 진단 페이지를 반환하지 않아야 한다.
- Spring이 자동 구성한 ProblemDetail의 detail·instance·확장 필드를 검토 없이 복사하지 않는다. 표준 기능 사용 여부와 별개로 최종 출력은 이 문서의 허용 목록으로 제한한다.
- 기본 오류 설정은 [CFG06](security/configuration.md)의 값을 적용한다. 이 설정은 커스텀 처리기·필터가 직접 작성한 응답을 정제하지 않으므로 공통 처리 구현을 대체하지 못한다.
- 이미 응답이 전송되었거나 직렬화 중 연결이 끝난 경우 원문 예외를 덧붙이거나 두 번째 JSON을 쓰지 않는다. 가능한 안전한 종료·정제 로그만 수행한다. 컨테이너/프록시가 직접 생성하는 응답도 해당 계층에서 진단 정보가 없는지 확인한다.
- 빈 catch·성공 응답·빈 목록으로 실제 실패를 감추지 않는다. 클라이언트에는 실패 상태를 알리되 내부 원인을 공개하지 않는 계약이다.

### 진단 로그

traceId·오류 분류·발생 위치와 정제한 원인/스택을 제한된 서버 로그에 남긴다. 같은 예외를 모든 서비스/Mapper에서 반복 기록하지 않는다. 업무 코드가 예외를 변환하면 내부 cause를 보존하고 최종 처리 경계가 진단 기록을 책임진다.

로그도 비밀 저장소가 아니다. 토큰·쿠키·비밀번호·개인정보·SQL binding 값이 중첩 예외 메시지·스택·외부 응답에서 재노출되지 않도록 정제한다. 응답에 서버 로그 전문을 연결하거나 브라우저 콘솔에 raw 오류를 출력하지 않는다.

### 1차 비노출 검증

합성 표식 SYNTHETIC_ERROR_MARKER를 예외 메시지·중첩 cause·외부 실패 본문에 넣어 공통 처리 경계를 검사한다. 실제 비밀값이나 실제 DB 오류를 일부러 만들 필요는 없다.

검사는 미분류 예외·승인 업무 오류·형식 오류·필드 검증·401/403·필터 오류·/error fallback을 포함한다. 해당 오류를 만들기 위해 공개 업무 endpoint나 운영용 장애 유발 기능을 추가하지 않고 기본 JUnit/Spring 검증 구성에서 확인한다. 데이터 저장소 오류는 합성 예외로 경계를 검사하며 실제 연결 장애 시험은 승인된 격리 환경에서 별도 수행한다.

통과 조건은 올바른 HTTP 상태·고정 code·안전한 title·traceId 일치, 허용 필드만 존재, 합성 표식과 원문 예외 정보가 응답 body/헤더/HTML에 없음이다. JSON 요청과 HTML Accept, ?trace=true&message=true&errors=true 같은 노출 유도 요청도 확인한다. 헤더·본문이 전송된 후 실패하는 경우의 한계는 별도로 기록한다.

검증 결과는 V15·V22의 해당 범위에 연결한다. 이번 문서는 계약 작성이며 아직 처리기 코드나 실제 비노출 시험을 실행한 것은 아니다.

## 후속 백엔드 계약 — 2026-10-29

공개 날짜·정밀숫자·null·안전정수는 [최종 설계 1단계](backend-foundation.md), 로그인 payload/상태/오류는 [API 인계표](backend-handoff.md), JWT·CSRF·cookie는 [인증](authentication-foundation.md)이 소유한다. 기존 envelope/허용 오류필드/예외 비노출은 변경하지 않는다. 개별 API가 아직 미정이라는 본문 표현은 1차 당시 경계이며 이번 표에 나온 API는 확정이다.

추가 code: LOGIN_FAILED/TOKEN_EXPIRED/TOKEN_REVOKED/TOKEN_REFRESH_REUSED(401), ORIGIN_REJECTED(403), CONCURRENT_LOGIN_DENIED/PASSWORD_LOGIN_UNAVAILABLE(409), CURRENT_PASSWORD_INVALID/PASSWORD_UNCHANGED(400), AUTH_STATE_UNAVAILABLE/AUDIT_UNAVAILABLE(503). title은 안전 HTTP제목 유지, 오류에 새 message/detail/params/토큰을 추가하지 않는다.

설정 오류/저장소 장애는503으로 구분하고 인증 실패401로 바꾸지 않는다. TOKEN_EXPIRED 응답은 refresh 쿠키를 삭제하지 않으며 refresh 자체의401은 자동갱신하지 않는다. 일반API의 세션 cookie 인증은 허용하지 않는다.

## 파일 API의 응답

[파일 기능 설계](attack-defense-and-files.md)의 파일 업로드·목록은 기존 성공 envelope를 사용한다. 업로드는 `multipart/form-data`의 `file` 한 개이며 생성 성공은201과 Location을 반환한다. 다운로드 성공만 이미지 바이너리 응답이며 JSON envelope로 감싸지 않는다. 다운로드 실패는 기존 안전한 오류 형식이므로 프론트는 Blob을 성공 파일로 저장하기 전에 상태와 형식을 확인한다. 삭제 성공204에는 응답 본문이 없다. 목록·다운로드·삭제 모두 서버에서 소유자를 검사한다. 정확한 타입·필수 필드·헤더는 [실제 OpenAPI](../api/openapi.json)를 따른다.