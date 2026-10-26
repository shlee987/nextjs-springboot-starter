'use client';

import axios, { type AxiosRequestConfig } from 'axios';
import { z } from 'zod';

import { ApiError, normalizeApiError } from './errors';

/** 브라우저 탭 메모리만 사용하며 토큰 발급 endpoint는 인증 단계에서 연결한다. */
let csrfToken: { token: string; headerName: string } | undefined;
const client = axios.create({
  baseURL: '/',
  withCredentials: true,
  timeout: 15_000,
  withXSRFToken: false,
});
const unauthenticatedListeners = new Set<() => void>();
const envelopeSchema = z.object({ data: z.unknown(), traceId: z.uuidv4() });

// Axios가 정규화한 헤더에만 CSRF를 추가하고 변경 요청을 자동 재전송하지 않는다.
client.interceptors.request.use((request) => {
  if (!['GET', 'HEAD', 'OPTIONS'].includes((request.method ?? 'GET').toUpperCase())) {
    if (!csrfToken) {
      throw new ApiError('CSRF_TOKEN_MISSING');
    }

    request.headers.set(csrfToken.headerName, csrfToken.token);
  }

  return request;
});

/** 인증 수명주기에서 받은 CSRF 토큰을 갱신하거나 폐기한다. 인증 증명은 저장하지 않는다. */
export function setCsrfToken(token: { token: string; headerName: string } | undefined): void {
  if (
    token &&
    (!/^[!#$%&'*+.^_`|~0-9A-Za-z-]+$/.test(token.headerName) ||
      !/^X-CSRF-TOKEN$/i.test(token.headerName) ||
      !token.token ||
      /[\r\n]/.test(token.token))
  ) {
    throw new ApiError('CSRF_TOKEN_INVALID');
  }

  csrfToken = token ? { ...token } : undefined;
}

/** Provider가 401에 따른 캐시 폐기를 연결하고 해제할 수 있게 한다. */
export function subscribeUnauthenticated(listener: () => void): () => void {
  unauthenticatedListeners.add(listener);

  return () => {
    unauthenticatedListeners.delete(listener);
  };
}

/** Orval의 성공 JSON 타입을 그대로 반환하며 envelope를 자동으로 unwrap하지 않는다. */
export async function apiMutator<T>(
  config: AxiosRequestConfig,
  options?: AxiosRequestConfig,
): Promise<T> {
  const request = { ...config, ...options };

  try {
    // 생성 spec도 외부 입력이다. 같은 출처의 /api 경로만 전송한다.
    if (!request.url?.startsWith('/api/') && request.url !== '/api') {
      throw new ApiError('MALFORMED_REQUEST');
    }

    const parsedUrl = new URL(request.url, 'https://request.invalid');

    if (
      request.url.includes('\\') ||
      request.url.includes('#') ||
      (parsedUrl.pathname !== '/api' && !parsedUrl.pathname.startsWith('/api/'))
    ) {
      throw new ApiError('MALFORMED_REQUEST');
    }

    const response = await client.request<T>({
      ...request,
      baseURL: '/',
      withCredentials: true,
      timeout: 15_000,
      withXSRFToken: false,
    });

    if (response.status !== 204 && (request.method ?? 'GET').toUpperCase() !== 'HEAD') {
      const contentType = String(response.headers['content-type'] ?? '').split(';')[0];

      if (
        contentType !== 'application/json' ||
        !envelopeSchema.safeParse(response.data).success ||
        typeof response.data !== 'object' ||
        response.data === null ||
        !Object.hasOwn(response.data, 'data')
      ) {
        throw new ApiError('UNEXPECTED_ERROR', response.status);
      }
    }

    return response.data;
  } catch (error) {
    const safeError = normalizeApiError(error);

    if (safeError.status === 401) {
      csrfToken = undefined;
      unauthenticatedListeners.forEach((listener) => listener());
    }

    throw safeError;
  }
}

/** 생성 Hook의 오류 타입을 공통 오류로 유지한다. */
export type ErrorType<T> = ApiError & { readonly errorType?: T };
/** 생성 mutation이 선언한 요청 본문 타입을 그대로 사용한다. */
export type BodyType<T> = T;
