'use client';

import axios, { type AxiosRequestConfig } from 'axios';
import { z } from 'zod';

import { ApiError, normalizeApiResponseError } from './errors';
import { readDownloadName } from './fileDownload';

/** 브라우저 탭 메모리만 사용하며 토큰 발급 endpoint는 인증 단계에서 연결한다. */
let csrfToken: { token: string; headerName: string } | undefined;
const client = axios.create({
  baseURL: '/',
  withCredentials: true,
  timeout: 15_000,
  withXSRFToken: false,
});
const unauthenticatedListeners = new Set<() => void>();
let authenticationGeneration = 0;
let recoverAuthentication: (() => Promise<void>) | undefined;
let recoverCsrf: (() => Promise<void>) | undefined;
let csrfFlight: Promise<void> | undefined;
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

/** 사용자 전환 전의 늦은 응답이 새 Query 캐시에 주입되지 않도록 세대를 증가시킨다. */
export function advanceAuthenticationGeneration(): number {
  return ++authenticationGeneration;
}

/** QueryKey가 현재 인증 세대를 포함하도록 읽기 전용 값을 제공한다. */
export function getAuthenticationGeneration(): number {
  return authenticationGeneration;
}

/** 브라우저 인증 Provider가 제한된 갱신 경로를 같은 요청 경계에 연결한다. */
export function registerAuthenticationRecovery(recover: () => Promise<void>): () => void {
  recoverAuthentication = recover;

  return () => {
    recoverAuthentication = undefined;
  };
}

/** 토큰이 다른 탭에서 바뀌었을 때 발급 endpoint만 재조회하고 원래 변경 요청은 반복하지 않는다. */
export function registerCsrfRecovery(recover: () => Promise<void>): () => void {
  recoverCsrf = recover;

  return () => {
    recoverCsrf = undefined;
  };
}

export type ApiRequestOptions = AxiosRequestConfig & {
  skipAuthRecovery?: boolean;
  /** 검증된 attachment 이름만 전달하며 응답 원문 헤더를 UI에 넘기지 않는다. */
  onDownloadFileName?: (name: string) => void;
};

/** Orval JSON envelope를 유지하고 파일 다운로드만 제한된 바이너리 계약으로 검증한다. */
export async function apiMutator<T>(
  config: AxiosRequestConfig,
  options?: ApiRequestOptions,
): Promise<T> {
  const { skipAuthRecovery, onDownloadFileName, ...axiosOptions } = options ?? {};
  const request = { ...config, ...axiosOptions };
  const generation = authenticationGeneration;

  if (typeof window === 'undefined') {
    throw new ApiError('MALFORMED_REQUEST');
  }

  return send(true);

  /** 안전한 조회의 TOKEN_EXPIRED만 한 번 복구하며 변경 요청은 재전송하지 않는다. */
  async function send(canRecover: boolean): Promise<T> {
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

      if (generation !== authenticationGeneration || request.signal?.aborted) {
        throw new ApiError('REQUEST_CANCELLED');
      }

      if (request.responseType === 'blob' && response.status !== 200) {
        throw new ApiError('UNEXPECTED_ERROR', response.status);
      }

      const method = (request.method ?? 'GET').toUpperCase();

      if (
        (method === 'POST' && parsedUrl.pathname === '/api/files' && response.status !== 201) ||
        (method === 'DELETE' &&
          /^\/api\/files\/[0-9a-f-]{36}$/i.test(parsedUrl.pathname) &&
          response.status !== 204)
      ) {
        throw new ApiError('UNEXPECTED_ERROR', response.status);
      }

      if (response.status !== 204 && (request.method ?? 'GET').toUpperCase() !== 'HEAD') {
        const contentType = String(response.headers['content-type'] ?? '').split(';')[0];

        if (request.responseType === 'blob') {
          if (
            (request.method ?? 'GET').toUpperCase() !== 'GET' ||
            !/^\/api\/files\/[0-9a-f-]{36}\/content$/i.test(parsedUrl.pathname) ||
            !['image/png', 'image/jpeg'].includes(contentType) ||
            !(response.data instanceof Blob) ||
            response.data.size < 1 ||
            response.data.size > 5 * 1024 * 1024 ||
            String(response.headers['x-content-type-options']).toLowerCase() !== 'nosniff' ||
            !String(response.headers['cache-control'])
              .split(',')
              .some((value) => value.trim().toLowerCase() === 'no-store')
          )
            throw new ApiError('UNEXPECTED_ERROR', response.status);

          const name = readDownloadName(
            String(response.headers['content-disposition'] ?? ''),
            contentType,
          );
          onDownloadFileName?.(name);
        } else if (
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
      const safeError = await normalizeApiResponseError(error);

      if (generation !== authenticationGeneration || request.signal?.aborted) {
        throw new ApiError('REQUEST_CANCELLED');
      }

      const isSafeMethod = ['GET', 'HEAD'].includes((request.method ?? 'GET').toUpperCase());
      const isAuthOperation =
        /^\/api\/auth\/(?:csrf|login|refresh|logout(?:-all)?|password)(?:\?|$)/.test(
          request.url ?? '',
        );

      if (
        canRecover &&
        !skipAuthRecovery &&
        !isAuthOperation &&
        isSafeMethod &&
        safeError.code === 'TOKEN_EXPIRED' &&
        recoverAuthentication
      ) {
        await recoverAuthentication();

        return send(false);
      }

      if (safeError.code === 'CSRF_TOKEN_INVALID' || safeError.code === 'CSRF_TOKEN_MISSING') {
        setCsrfToken(undefined);

        if (!/^\/api\/auth\/csrf(?:\?|$)/.test(request.url ?? '') && recoverCsrf) {
          csrfFlight ??= recoverCsrf().finally(() => {
            csrfFlight = undefined;
          });

          // 복구 실패도 원래 요청의 안전한 오류를 대체하거나 변경 요청을 재전송하지 않는다.
          await csrfFlight.catch(() => undefined);
        }
      }

      if (safeError.status === 401 && !skipAuthRecovery && !isAuthOperation) {
        csrfToken = undefined;
        unauthenticatedListeners.forEach((listener) => listener());
      }

      throw safeError;
    }
  }
}

/** 생성 Hook의 오류 타입을 공통 오류로 유지한다. */
export type ErrorType<T> = ApiError & { readonly errorType?: T };
/** 생성 mutation이 선언한 요청 본문 타입을 그대로 사용한다. */
export type BodyType<T> = T;
