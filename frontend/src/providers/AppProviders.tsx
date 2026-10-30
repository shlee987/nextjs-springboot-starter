'use client';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createContext, useContext, useState, type ReactNode } from 'react';

import { AuthProvider } from '@/features/auth/AuthProvider';
import { ToastProvider } from '@/components/ui/Feedback';
import { UiStoreProvider } from '@/stores/UiStore';

const NonceContext = createContext<string | undefined>(undefined);

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

  return (
    <NonceContext.Provider value={nonce}>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <UiStoreProvider>
            <ToastProvider>{children}</ToastProvider>
          </UiStoreProvider>
        </AuthProvider>
      </QueryClientProvider>
    </NonceContext.Provider>
  );
}

/** 향후 AG Grid styleNonce 등 문서 내부 스타일 경계에 요청 nonce를 전달한다. */
export function useDocumentNonce(): string | undefined {
  return useContext(NonceContext);
}
