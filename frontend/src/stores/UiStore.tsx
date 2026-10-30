'use client';

import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { createStore, useStore, type StoreApi } from 'zustand';

import { useSession } from '@/features/auth/AuthProvider';

export type DirectoryView = {
  draft: string;
  q: string;
  page: number;
  size: number;
  sort: 'idAsc' | 'nameAsc' | 'nameDesc';
};
export const DEFAULT_DIRECTORY_VIEW: DirectoryView = {
  draft: '',
  q: '',
  page: 0,
  size: 20,
  sort: 'idAsc',
};

type UiState = {
  collapsed: boolean;
  mobileOpen: boolean;
  setCollapsed: (collapsed: boolean) => void;
  setMobileOpen: (open: boolean) => void;
  directories: { users: DirectoryView; departments: DirectoryView };
  setDirectory: (kind: 'users' | 'departments', view: DirectoryView) => void;
};
const UiContext = createContext<StoreApi<UiState> | undefined>(undefined);

/** 표시 상태만 렌더링 트리별로 소유하며 사용자·API·폼 데이터를 저장하지 않는다. */
export function UiStoreProvider({ children }: { children: ReactNode }) {
  const { generation } = useSession();
  const [store] = useState(() =>
    createStore<UiState>((set) => ({
      collapsed: false,
      mobileOpen: false,
      setCollapsed: (collapsed) => set({ collapsed }),
      setMobileOpen: (mobileOpen) => set({ mobileOpen }),
      directories: { users: DEFAULT_DIRECTORY_VIEW, departments: DEFAULT_DIRECTORY_VIEW },
      setDirectory: (kind, view) =>
        set((state) => ({ directories: { ...state.directories, [kind]: view } })),
    })),
  );
  useEffect(() => {
    store.setState({
      directories: { users: DEFAULT_DIRECTORY_VIEW, departments: DEFAULT_DIRECTORY_VIEW },
    });
  }, [store, generation]);

  return <UiContext.Provider value={store}>{children}</UiContext.Provider>;
}

/** 동일 Provider 아래 메뉴와 헤더에서 공통 UI 상태를 구독한다. */
export function useUiStore<T>(selector: (state: UiState) => T): T {
  const store = useContext(UiContext);

  if (!store) {
    throw new Error('UiStoreProvider 내부에서 사용하세요.');
  }

  return useStore(store, selector);
}
