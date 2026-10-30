'use client';

import { useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react';

import {
  advanceAuthenticationGeneration,
  getAuthenticationGeneration,
  registerAuthenticationRecovery,
  registerCsrfRecovery,
  setCsrfToken,
  subscribeUnauthenticated,
} from '@/api/client';
import { ApiError } from '@/api/errors';
import { useChangePassword } from '@/api/generated/client';
import { ErrorState, Spinner } from '@/components/ui/Feedback';

import {
  fetchCsrf,
  fetchCurrentUser,
  headSession,
  recoverSession,
  submitLogin,
  submitLogout,
  submitPassword,
  withAuthenticationLock,
  type CurrentUser,
} from './session';

type SessionStatus = 'checking' | 'authenticated' | 'anonymous' | 'error';
type Session = {
  status: SessionStatus;
  user?: CurrentUser;
  error?: ApiError;
  generation: number;
  bootstrap: (force?: boolean) => Promise<void>;
  login: (loginId: string, password: string) => Promise<void>;
  logout: (all?: boolean) => Promise<void>;
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>;
};
const AuthContext = createContext<Session | undefined>(undefined);

/** 인증 데이터는 Query에만 보관하고 합성 카탈로그에서는 자동 인증 API를 호출하지 않는다. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [generation, setGeneration] = useState(getAuthenticationGeneration);
  const [status, setStatus] = useState<SessionStatus>('checking');
  const [error, setError] = useState<ApiError>();
  const channel = useRef<BroadcastChannel | undefined>(undefined);
  const bootFlight = useRef<{ generation: number; promise: Promise<void> } | undefined>(undefined);
  const checkedGeneration = useRef<number | undefined>(undefined);
  const userQuery = useQuery({
    queryKey: ['session', generation, 'me'],
    queryFn: fetchCurrentUser,
    enabled: false,
    retry: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    refetchInterval: false,
    staleTime: Infinity,
  });
  const passwordMutation = useChangePassword({
    mutation: {
      mutationFn: ({ data }) => performPasswordChange(data.currentPassword, data.newPassword),
      retry: false,
    },
  });

  /** 이전 세대 요청을 먼저 무효화하고 query·개인 메모리를 비운다. */
  const clearSession = useCallback(
    async (broadcast: boolean): Promise<number> => {
      const next = advanceAuthenticationGeneration();
      checkedGeneration.current = undefined;
      setGeneration(next);
      setStatus('anonymous');
      setError(undefined);
      setCsrfToken(undefined);
      await queryClient.cancelQueries();
      queryClient.clear();

      if (broadcast) {
        channel.current?.postMessage({ type: 'session-changed' });
      }

      return next;
    },
    [queryClient],
  );

  /** 제한된 갱신 실패의401만 인증을 정리하며 503/네트워크는 별도 장애로 남긴다. */
  const recover = useCallback(async (): Promise<void> => {
    try {
      await recoverSession();
    } catch (failure) {
      if (
        failure instanceof ApiError &&
        (failure.status === 401 || failure.code === 'TOKEN_EXPIRED')
      ) {
        await clearSession(true);
      }

      throw failure;
    }
  }, [clearSession]);

  useEffect(() => {
    const unsubscribe = subscribeUnauthenticated(() => {
      void clearSession(true);
    });
    const unregister = registerAuthenticationRecovery(recover);
    const unregisterCsrf = registerCsrfRecovery(fetchCsrf);
    const broadcast =
      typeof BroadcastChannel === 'undefined'
        ? undefined
        : new BroadcastChannel('starter-authentication');
    channel.current = broadcast;

    if (broadcast) {
      broadcast.onmessage = (event: MessageEvent<unknown>) => {
        if (
          typeof event.data === 'object' &&
          event.data !== null &&
          'type' in event.data &&
          event.data.type === 'session-changed'
        ) {
          void clearSession(false);
        }
      };
    }

    return () => {
      unsubscribe();
      unregister();
      unregisterCsrf();
      broadcast?.close();
      channel.current = undefined;
    };
  }, [clearSession, recover]);

  /** 세대별 확인을 공유하고 명시 재시도만 재확인한다. 본문 없는 HEAD 401도 한 번 복구한다. */
  function bootstrap(force = false): Promise<void> {
    const current = getAuthenticationGeneration();

    if (bootFlight.current?.generation === current) {
      return bootFlight.current.promise;
    }

    if (!force && checkedGeneration.current === current) {
      return Promise.resolve();
    }

    setStatus('checking');
    const promise = (async () => {
      try {
        await fetchCsrf();

        try {
          await headSession();
        } catch (failure) {
          if (!(failure instanceof ApiError) || failure.status !== 401) {
            throw failure;
          }

          await recoverSession();
        }

        const response = await fetchCurrentUser();

        if (current !== getAuthenticationGeneration()) {
          return;
        }

        queryClient.setQueryData(['session', current, 'me'], response);
        checkedGeneration.current = current;
        setGeneration(current);
        setStatus('authenticated');
        setError(undefined);
      } catch (failure) {
        if (current !== getAuthenticationGeneration()) {
          return;
        }
        if (
          failure instanceof ApiError &&
          (failure.status === 401 || failure.code === 'TOKEN_EXPIRED')
        ) {
          await clearSession(false);
        } else {
          setStatus('error');
          setError(failure instanceof ApiError ? failure : new ApiError('UNEXPECTED_ERROR'));
        }
      }
    })().finally(() => {
      if (current === getAuthenticationGeneration()) {
        checkedGeneration.current = current;
      }

      if (bootFlight.current?.promise === promise) {
        bootFlight.current = undefined;
      }
    });
    bootFlight.current = { generation: current, promise };

    return promise;
  }

  /** 로그인·로그아웃·비밀번호 변경도 refresh와 같은 탭간 잠금을 사용한다. */
  async function login(loginId: string, password: string) {
    await withAuthenticationLock(async () => {
      const current = await clearSession(true);
      await submitLogin(loginId, password);
      const response = await fetchCurrentUser();

      if (current !== getAuthenticationGeneration()) {
        throw new ApiError('REQUEST_CANCELLED');
      }
      queryClient.setQueryData(['session', current, 'me'], response);
      checkedGeneration.current = current;
      setGeneration(current);
      setStatus('authenticated');
      channel.current?.postMessage({ type: 'session-changed' });
    });
  }

  /** 통신 실패 시 서버 폐기를 확인하지 못했다고 호출부에 전달하며 성공으로 표시하지 않는다. */
  async function logout(all = false) {
    await withAuthenticationLock(async () => {
      await clearSession(true);
      await submitLogout(all);
      channel.current?.postMessage({ type: 'session-changed' });
    });
  }

  /** 비밀번호 변경 성공 후 이전 사용자 캐시와 입력을 폐기한다. */
  async function performPasswordChange(currentPassword: string, newPassword: string) {
    await withAuthenticationLock(async () => {
      await submitPassword(currentPassword, newPassword);
      await clearSession(true);
      await fetchCsrf();
    });
  }

  return (
    <AuthContext.Provider
      value={{
        status,
        user: userQuery.data?.data,
        error,
        generation,
        bootstrap,
        login,
        logout,
        changePassword: (currentPassword, newPassword) =>
          passwordMutation.mutateAsync({ data: { currentPassword, newPassword } }),
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

/** 현재 사용자와 인증 작업을 Query 기반 단일 경계에서 읽는다. */
export function useSession(): Session {
  const session = useContext(AuthContext);

  if (!session) {
    throw new Error('AuthProvider 내부에서 사용하세요.');
  }

  return session;
}

/** 서버 me의 permission만 화면 보호에 사용하고 JWT를 해독하지 않는다. */
export function usePermission(permission?: string): boolean {
  const { status, user } = useSession();

  return (
    status === 'authenticated' && (!permission || Boolean(user?.permissions.includes(permission)))
  );
}

/** 직접 URL도 인증·권한을 확인하며 실패와 익명 상태를 구분한다. */
export function PermissionGuard({
  permission,
  children,
}: {
  permission?: string;
  children: ReactNode;
}) {
  const session = useSession();
  const allowed = usePermission(permission);
  const bootstrap = useRef(session.bootstrap);

  useEffect(() => {
    void bootstrap.current();
  }, []);

  if (session.status === 'checking') {
    return <Spinner label="인증 확인 중" />;
  }

  if (session.status === 'error') {
    return (
      <ErrorState
        message={session.error?.message}
        traceId={session.error?.traceId}
        onRetry={() => void session.bootstrap(true)}
      />
    );
  }

  if (session.status === 'anonymous') {
    return (
      <p>
        로그인이 필요합니다. <Link href="/login">로그인</Link>
      </p>
    );
  }

  if (!allowed) {
    return <ErrorState message="이 화면을 조회할 권한이 없습니다." />;
  }

  return <>{children}</>;
}
