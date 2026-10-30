'use client';

import {
  createContext,
  useContext,
  useLayoutEffect,
  useMemo,
  useSyncExternalStore,
  type ReactNode,
} from 'react';
import { createActionScope } from '@/lib/action-scope';

type ActionContextValue = {
  identity: string;
  parent?: ActionContextValue;
  scope: ReturnType<typeof createActionScope>;
};
const ActionContext = createContext<ActionContextValue | undefined>(undefined);

/** 같은 화면의 버튼은 작업 키를 공유하고 identity가 바뀌면 이전 범위와 잠금을 분리한다. */
export function ActionProvider({
  identity,
  children,
  isCurrent,
}: {
  identity: string;
  children: ReactNode;
  isCurrent?: () => boolean;
}) {
  const parent = useContext(ActionContext);
  const value = useMemo(
    () => ({
      identity,
      parent,
      scope: createActionScope(() => (parent?.scope.isActive() ?? true) && (isCurrent?.() ?? true)),
    }),
    [identity, parent, isCurrent],
  );
  useLayoutEffect(() => value.scope.retain(), [value]);

  return <ActionContext.Provider value={value}>{children}</ActionContext.Provider>;
}

/** pending은 동일 키의 모든 버튼에 공유된다. run의 실패는 호출자가 기존 오류 경계에서 처리한다. */
export function useAction(key: string) {
  const context = useContext(ActionContext);

  if (!context) throw new Error('ActionProvider 내부에서 사용하세요.');

  const scope = context.scope;

  const pending = useSyncExternalStore(
    scope.subscribe,
    () => scope.isRunning(key),
    () => false,
  );

  return {
    pending,
    isRunning: () => scope.isRunning(key),
    run: <T,>(task: (signal: AbortSignal) => T | Promise<T>) => scope.run(key, task),
    cancel: () => scope.cancel(key),
  };
}
