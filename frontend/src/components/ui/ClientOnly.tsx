'use client';

import { useSyncExternalStore, type ReactNode } from 'react';

/** 브라우저 전용 라이브러리의 SSR 인라인 스타일과 hydration 불일치를 방지한다. */
export function ClientOnly({
  children,
  fallback = null,
}: {
  children: ReactNode;
  fallback?: ReactNode;
}) {
  const mounted = useSyncExternalStore(
    subscribe,
    () => true,
    () => false,
  );

  return mounted ? children : fallback;
}

/** 외부 상태 변경이 없는 client/server snapshot 경계다. */
function subscribe() {
  return () => {};
}
