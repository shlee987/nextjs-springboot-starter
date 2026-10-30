'use client';

import { z } from 'zod';

import { setCsrfToken } from '@/api/client';
import {
  changePassword,
  getCsrf,
  getCurrentUser,
  headCurrentUser,
  login,
  logout,
  logoutAll,
  refresh,
} from '@/api/generated/client';
import { ApiError } from '@/api/errors';
import type { ApiResponse } from '@/api/models';

const currentUserSchema = z.object({
  userId: z.string().regex(/^\d+$/),
  loginId: z.string(),
  displayName: z.string(),
  departments: z.array(
    z.object({ departmentId: z.string(), departmentName: z.string(), primary: z.boolean() }),
  ),
  roles: z.array(z.string()),
  permissions: z.array(z.string()),
});
export type CurrentUser = z.infer<typeof currentUserSchema>;
let refreshFlight: Promise<void> | undefined;
let explicitQueue: Promise<unknown> = Promise.resolve();

/** 쿠키는 읽지 않고 발급 API가 반환한 CSRF 표현만 탭 메모리에 둔다. */
export async function fetchCsrf(): Promise<void> {
  const response = await getCsrf({ skipAuthRecovery: true });
  const result = z
    .object({ token: z.string().min(1), headerName: z.literal('X-CSRF-TOKEN') })
    .safeParse(response.data);

  if (!result.success) {
    throw new ApiError('UNEXPECTED_ERROR');
  }

  setCsrfToken(result.data);
}

/** HEAD는 활동 시간을 연장하지 않는 인증 유효성 확인이다. */
export async function headSession(): Promise<void> {
  await headCurrentUser({ skipAuthRecovery: true });
}

/** 권한 판단은 JWT 해독 대신 서버가 검증한 현재 사용자 응답을 사용한다. */
export async function fetchCurrentUser(): Promise<ApiResponse<CurrentUser>> {
  const response = await getCurrentUser({ skipAuthRecovery: true });
  const result = currentUserSchema.safeParse(response.data);

  if (!result.success || !response.traceId) {
    throw new ApiError('UNEXPECTED_ERROR');
  }

  return { data: result.data, traceId: response.traceId };
}

/** 같은 origin의 인증 변경을 Web Locks로 직렬화한다. 미지원은 명시 조작만 탭 안에서 직렬화한다. */
export async function withAuthenticationLock<T>(operation: () => Promise<T>): Promise<T> {
  if (navigator.locks) {
    return navigator.locks.request('starter-authentication', operation);
  }

  const flight = explicitQueue.then(operation, operation);
  explicitQueue = flight.then(
    () => undefined,
    () => undefined,
  );

  return flight;
}

/** 탭내 single-flight와 탭간 lock 뒤 HEAD 확인으로 refresh 회전을 한 번만 수행한다. */
export function recoverSession(): Promise<void> {
  if (!navigator.locks) {
    return Promise.reject(new ApiError('TOKEN_EXPIRED'));
  }

  if (refreshFlight) {
    return refreshFlight;
  }

  refreshFlight = withAuthenticationLock(async () => {
    try {
      await headSession();

      return;
    } catch (error) {
      // HEAD의 오류 본문은 없으므로 이 경로에서만 HTTP 401을 판단한다.
      if (!(error instanceof ApiError) || error.status !== 401) {
        throw error;
      }
    }

    await fetchCsrf();
    await refresh({ skipAuthRecovery: true });
  }).finally(() => {
    refreshFlight = undefined;
  });

  return refreshFlight;
}

/** 계약에 명시된 로그인 요청만 전송하고 토큰 본문을 저장하지 않는다. */
export async function submitLogin(loginId: string, password: string): Promise<void> {
  await fetchCsrf();
  await login({ loginId, password }, { skipAuthRecovery: true });
  await fetchCsrf();
}

/** 현재 또는 전체 인증 폐기는 성공 응답을 받은 경우에만 완료로 취급한다. */
export async function submitLogout(all: boolean): Promise<void> {
  await fetchCsrf();
  await (all ? logoutAll : logout)({ skipAuthRecovery: true });
  setCsrfToken(undefined);
  await fetchCsrf();
}

/** 확인 비밀번호는 보내지 않으며 서버가 전체 sid를 폐기한 뒤 재로그인한다. */
export async function submitPassword(currentPassword: string, newPassword: string): Promise<void> {
  await fetchCsrf();
  await changePassword({ currentPassword, newPassword }, { skipAuthRecovery: true });
}
