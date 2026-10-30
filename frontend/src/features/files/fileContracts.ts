import { z } from 'zod';
import { parseApiDate } from '@/lib/dates';

/** Controller FileInfo의 실제 응답을 검증한다. 파일명은 표시용이며 서버 경로로 사용하지 않는다. */
export const fileInfoSchema = z
  .object({
    fileId: z.uuid(),
    originalName: z
      .string()
      .min(1)
      .refine((value) => Array.from(value).length <= 120),
    contentType: z.enum(['image/png', 'image/jpeg']),
    size: z
      .number()
      .int()
      .min(1)
      .max(5 * 1024 * 1024),
    width: z.number().int().min(1).max(8192),
    height: z.number().int().min(1).max(8192),
    createdAt: z.string().refine((value) => {
      try {
        parseApiDate(value, 'millisecond');

        return true;
      } catch {
        return false;
      }
    }),
  })
  .refine((file) => file.width * file.height <= 16_000_000);
