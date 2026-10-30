/** 서버 원문 대신 표시할 승인된 공통 한국어 문구다. 업무 문구도 이 경계에 등록한다. */
export const messages = {
  FILE_NAME_INVALID: '파일 이름을 확인해 주세요. 최대 120자까지 사용할 수 있습니다.',
  FILE_TYPE_UNSUPPORTED: '지원하지 않는 파일 형식입니다. PNG 또는 JPEG 이미지를 선택해 주세요.',
  FILE_CONTENT_INVALID: '이미지 내용을 확인할 수 없습니다. 올바른 이미지 파일을 선택해 주세요.',
  FILE_QUOTA_EXCEEDED:
    '파일 저장 공간이 부족합니다. 필요 없는 파일을 삭제한 뒤 다시 시도해 주세요.',
  FILE_PROCESSING_BUSY: '파일 처리 요청이 많습니다. 잠시 후 다시 시도해 주세요.',
  FILE_STORAGE_UNAVAILABLE: '파일 저장소를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.',
  FILE_METADATA_UNAVAILABLE: '파일 정보를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.',
  LOGIN_FAILED: '아이디 또는 비밀번호를 확인해 주세요.',
  TOKEN_EXPIRED: '인증 유효기간이 끝났습니다. 다시 로그인해 주세요.',
  TOKEN_REVOKED: '인증이 종료되었습니다. 다시 로그인해 주세요.',
  TOKEN_REFRESH_REUSED: '인증 갱신을 확인할 수 없습니다. 다시 로그인해 주세요.',
  CONCURRENT_LOGIN_DENIED: '다른 로그인 상태 때문에 새 로그인할 수 없습니다.',
  ORIGIN_REJECTED: '허용된 서비스 주소에서 다시 시도해 주세요.',
  CURRENT_PASSWORD_INVALID: '현재 비밀번호가 올바르지 않습니다.',
  PASSWORD_UNCHANGED: '새 비밀번호는 현재 비밀번호와 달라야 합니다.',
  PASSWORD_LOGIN_UNAVAILABLE: '이 계정은 자체 비밀번호를 변경할 수 없습니다.',
  AUTH_STATE_UNAVAILABLE: '인증 서비스를 일시적으로 사용할 수 없습니다.',
  AUDIT_UNAVAILABLE: '이력 서비스를 일시적으로 사용할 수 없습니다.',
  UNEXPECTED_ERROR: '처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.',
  NETWORK_ERROR: '서버에 연결할 수 없습니다.',
  REQUEST_TIMEOUT: '요청 시간이 초과되었습니다.',
  REQUEST_CANCELLED: '요청이 취소되었습니다.',
  VALIDATION_FAILED: '입력한 내용을 확인해 주세요.',
  MALFORMED_REQUEST: '요청 형식을 확인해 주세요.',
  UNAUTHENTICATED: '인증이 필요합니다.',
  ACCESS_DENIED: '접근 권한이 없습니다.',
  CSRF_TOKEN_MISSING: '보안 확인이 필요합니다.',
  CSRF_TOKEN_INVALID: '보안 정보를 갱신한 후 다시 시도해 주세요.',
  NOT_FOUND: '요청한 정보를 찾을 수 없습니다.',
  METHOD_NOT_ALLOWED: '지원하지 않는 요청입니다.',
  CONFLICT: '정보가 변경되었습니다. 확인 후 다시 시도해 주세요.',
  PAYLOAD_TOO_LARGE: '요청 크기가 너무 큽니다.',
  UNSUPPORTED_MEDIA_TYPE: '지원하지 않는 요청 형식입니다.',
  RATE_LIMITED: '요청이 많습니다. 잠시 후 다시 시도해 주세요.',
  INTERNAL_ERROR: '서버에서 요청을 처리하지 못했습니다.',
  DEPENDENCY_UNAVAILABLE: '서비스를 일시적으로 사용할 수 없습니다.',
} as const;

/** 공개 필드 식별자는 실제 폼이 허용한 목록과 대조한 뒤 사용한다. */
export const fieldMessages = {
  REQUIRED: '필수 입력 항목입니다.',
  INVALID_FORMAT: '입력 형식이 올바르지 않습니다.',
  OUT_OF_RANGE: '허용 범위를 확인해 주세요.',
  INVALID_LENGTH: '입력 길이를 확인해 주세요.',
} as const;
/** 로그인 화면과 연결 예제에서 같은 상태를 쉬운 한국어로 표시한다. */
export const sessionStatusLabels = {
  checking: '확인 전',
  authenticated: '로그인됨',
  anonymous: '로그인 필요',
  error: '확인 실패',
};
