'use client';

import { usePathname } from 'next/navigation';
import { useCallback, type ReactNode } from 'react';
import { getAuthenticationGeneration } from '@/api/client';
import { useSession } from '@/features/auth/AuthProvider';
import { ActionProvider } from './ActionProvider';

/** 인증 세대와 화면 경로로 작업 범위를 나눠 사용자·화면 전환의 같은 문자열 키가 충돌하지 않게 한다. */
export function ScreenActionScope({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { generation } = useSession();
  const isCurrent = useCallback(() => getAuthenticationGeneration() === generation, [generation]);

  return (
    <ActionProvider identity={`${generation}:${pathname}`} isCurrent={isCurrent}>
      {children}
    </ActionProvider>
  );
}
