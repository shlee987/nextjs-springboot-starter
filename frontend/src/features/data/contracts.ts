import { z } from 'zod';

import { ApiError } from '@/api/errors';
import { parseApiDate } from '@/lib/dates';

const id = z.string().regex(/^[1-9][0-9]{0,18}$/);
const count = z.number().int().nonnegative().max(Number.MAX_SAFE_INTEGER);
const timestamp = z.string().refine((value) => {
  try {
    parseApiDate(value, 'millisecond');
    return true;
  } catch {
    return false;
  }
});
export const userSummarySchema = z.object({
  userId: id,
  displayName: z.string(),
  status: z.string(),
  departments: z.array(z.object({ departmentId: id, departmentName: z.string() })),
});
export const profileSchema = userSummarySchema.extend({
  loginId: z.string(),
  email: z.string().nullable(),
  createdAt: timestamp,
  updatedAt: timestamp,
});
export const departmentSchema = z.object({
  departmentId: id,
  departmentCode: z.string(),
  departmentName: z.string(),
  parentId: id.nullable(),
  sortOrder: z.number().int(),
  active: z.boolean(),
});
const auditCommon = z.object({
  occurredAt: timestamp,
  actorUserId: id.nullable(),
  outcome: z.string(),
  reasonCode: z.string().nullable(),
  traceId: z.string(),
});
export const accessSchema = auditCommon.extend({
  auditId: z.string().min(1),
  resourceCode: z.string(),
  actionCode: z.string(),
  targetType: z.string().nullable(),
  targetId: z.string().nullable(),
});
export const securitySchema = auditCommon.extend({
  eventId: z.string().min(1),
  eventCode: z.string(),
  targetUserId: id.nullable(),
});

/** 생성 타입과 별개로 실제 응답을 화면 계약에 맞게 검사하고 원문 오류를 숨긴다. */
export function readEnvelope<T>(schema: z.ZodType<T>, value: unknown) {
  const parsed = z.object({ data: schema, traceId: z.uuidv4() }).safeParse(value);

  if (!parsed.success) {
    throw new ApiError('UNEXPECTED_ERROR');
  }

  return parsed.data;
}

/** 목록의 안전 정수·페이지 상한과 행 형태를 검사하며 실패를 빈 결과로 바꾸지 않는다. */
export function pageSchema<T>(item: z.ZodType<T>) {
  return z.object({
    items: z.array(item).max(100),
    page: count,
    size: z.number().int().min(1).max(100),
    totalElements: count,
    totalPages: count,
  });
}
