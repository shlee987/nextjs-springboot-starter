/** 서버 원문 대신 표시할 승인된 공통 한국어 문구다. 업무 문구도 이 경계에 등록한다. */
export const messages = {
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
