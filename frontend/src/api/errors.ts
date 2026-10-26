import axios from 'axios';
import { z } from 'zod';

import { fieldMessages, messages } from '@/messages/messages';

const apiCodes = z.enum([
  'VALIDATION_FAILED',
  'MALFORMED_REQUEST',
  'UNAUTHENTICATED',
  'ACCESS_DENIED',
  'CSRF_TOKEN_MISSING',
  'CSRF_TOKEN_INVALID',
  'NOT_FOUND',
  'METHOD_NOT_ALLOWED',
  'CONFLICT',
  'PAYLOAD_TOO_LARGE',
  'UNSUPPORTED_MEDIA_TYPE',
  'RATE_LIMITED',
  'INTERNAL_ERROR',
  'DEPENDENCY_UNAVAILABLE',
]);
const problemSchema = z.object({
  type: z.literal('about:blank'),
  title: z.string(),
  status: z.number().int().min(400).max(599),
  code: apiCodes,
  traceId: z.uuidv4(),
  fieldErrors: z
    .array(
      z.object({
        field: z
          .string()
          .max(100)
          .regex(/^[a-zA-Z][a-zA-Z0-9_.\[\]]*$/),
        code: z.enum(['REQUIRED', 'INVALID_FORMAT', 'OUT_OF_RANGE', 'INVALID_LENGTH']),
      }),
    )
    .max(100)
    .optional(),
});

/** Axios 응답·원인·요청 설정을 보관하지 않는 공개 가능한 오류다. */
export class ApiError extends Error {
  /** 승인된 문구와 진단 식별자만 오류 객체에 보존한다. */
  constructor(
    public readonly code: keyof typeof messages,
    public readonly status?: number,
    public readonly traceId?: string,
    public readonly fieldErrors: { field: string; code: keyof typeof fieldMessages }[] = [],
  ) {
    super(messages[code]);
    this.name = 'ApiError';
  }
}

/** 프록시 HTML·미등록 코드·네트워크 실패를 원문 없이 하나의 오류 계약으로 바꾼다. */
export function normalizeApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }

  if (axios.isCancel(error)) {
    return new ApiError('REQUEST_CANCELLED');
  }

  if (!axios.isAxiosError(error)) {
    return new ApiError('UNEXPECTED_ERROR');
  }

  if (!error.response) {
    return new ApiError(
      error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT'
        ? 'REQUEST_TIMEOUT'
        : 'NETWORK_ERROR',
    );
  }

  const status = error.response.status;
  const contentType = String(error.response.headers['content-type'] ?? '').split(';')[0];
  const problem =
    contentType === 'application/problem+json'
      ? problemSchema.safeParse(error.response.data)
      : undefined;

  if (problem?.success && problem.data.status === status) {
    const fields = problem.data.fieldErrors ?? [];

    return new ApiError(
      problem.data.code,
      status,
      problem.data.traceId,
      fields.filter(
        (item, index) =>
          fields.findIndex((other) => other.field === item.field && other.code === item.code) ===
          index,
      ),
    );
  }

  return new ApiError('UNEXPECTED_ERROR', status);
}
