import type { FieldValues, Path, UseFormReturn } from 'react-hook-form';

import { ApiError } from '@/api/errors';
import { fieldMessages, messages } from '@/messages/messages';

/** 허용한 공개 필드만 RHF에 매핑하고 나머지는 안전한 폼 오류 한 곳에 표시한다. */
export function applyApiFormError<T extends FieldValues>(
  error: unknown,
  form: UseFormReturn<T>,
  fields: Path<T>[],
): void {
  if (!(error instanceof ApiError)) {
    form.setError('root', { message: messages.UNEXPECTED_ERROR });
    return;
  }

  let hasField = false;

  for (const entry of error.fieldErrors) {
    const field = fields.find((allowed) => allowed === entry.field);

    if (field) {
      form.setError(field, { type: 'server', message: fieldMessages[entry.code] });
      hasField = true;
    }
  }

  if (!hasField) {
    form.setError('root', {
      message: `${error.message}${error.retryAfterSeconds !== undefined ? ` ${error.retryAfterSeconds}초 후 다시 시도하세요.` : ''}${error.traceId ? ` (진단 ID: ${error.traceId})` : ''}`,
    });
  }
}
