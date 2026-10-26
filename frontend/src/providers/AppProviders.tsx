'use client';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';

import { setCsrfToken, subscribeUnauthenticated } from '@/api/client';

const NonceContext = createContext<string | undefined>(undefined);
const ResetContext = createContext<(() => Promise<void>) | undefined>(undefined);

/** QueryClient를 렌더링 트리별로 생성해 서버 요청 사이에 캐시가 공유되지 않게 한다. */
export function AppProviders({ nonce, children }: { nonce?: string; children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { retry: false, staleTime: 0 },
          mutations: { retry: false },
        },
      }),
  );

  /** 인증 전환 시 진행 중 요청을 취소하고 이전 사용자의 캐시·CSRF 메모리를 지운다. */
  async function resetSessionState(): Promise<void> {
    setCsrfToken(undefined);
    await queryClient.cancelQueries();
    queryClient.clear();
  }

  useEffect(
    () =>
      subscribeUnauthenticated(() => {
        void queryClient.cancelQueries();
        queryClient.clear();
      }),
    [queryClient],
  );

  return (
    <NonceContext.Provider value={nonce}>
      <ResetContext.Provider value={resetSessionState}>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </ResetContext.Provider>
    </NonceContext.Provider>
  );
}

/** 향후 AG Grid styleNonce 등 문서 내부 스타일 경계에 요청 nonce를 전달한다. */
export function useDocumentNonce(): string | undefined {
  return useContext(NonceContext);
}

/** 로그인·로그아웃·계정 전환의 캐시 폐기 연결점이며 인증 네트워크 흐름은 수행하지 않는다. */
export function useResetSessionState(): () => Promise<void> {
  const reset = useContext(ResetContext);

  if (!reset) {
    throw new Error('AppProviders 내부에서 사용해야 합니다.');
  }

  return reset;
}
