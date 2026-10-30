'use client';

import Link from 'next/link';
import { useState, useSyncExternalStore } from 'react';
import { useQueryClient } from '@tanstack/react-query';

import { ApiError, normalizeApiError } from '@/api/errors';
import { useGetMyProfile } from '@/api/generated/client';
import { Button } from '@/components/ui/Button';
import { InlineAlert } from '@/components/ui/Feedback';
import { ClientOnly } from '@/components/ui/ClientOnly';
import { useSession } from '@/features/auth/AuthProvider';
import { recoverSession } from '@/features/auth/session';
import { profileSchema, readEnvelope } from '@/features/data/contracts';
import { sessionStatusLabels } from '@/messages/messages';

import { ExampleSection } from './ExampleSection';

/** 실제 생성 API 호출과 합성 오류 표시의 실행 상태를 구분한다. */
export function ApiExample() {
  const [error, setError] = useState<ApiError>();
  const session = useSession();
  const queryClient = useQueryClient();
  const key = ['api-example', session.generation, 'my-profile'];
  const query = useGetMyProfile({
    query: {
      queryKey: key,
      enabled: false,
      staleTime: 60_000,
      refetchOnWindowFocus: false,
      select: (response) => readEnvelope(profileSchema, response),
    },
  });
  const cacheInvalidated = useSyncExternalStore(
    (onChange) => queryClient.getQueryCache().subscribe(onChange),
    () => queryClient.getQueryState(key)?.isInvalidated ?? false,
    () => false,
  );

  return (
    <ExampleSection
      title="내 프로필 조회와 캐시"
      description="실제 OpenAPI에서 생성한 내 프로필 조회 Hook을 사용합니다. 인증 후 명시 버튼으로 호출·취소·캐시 무효화를 확인합니다. 아래 합성 오류 표시는 실제 응답과 별도입니다."
      code={
        "import { useGetMyProfile } from '@/api/generated/client';\nconst query = useGetMyProfile({ query: { enabled: false, queryKey: ['api-example', generation, 'my-profile'] } });\n// query.refetch() / queryClient.cancelQueries({ queryKey })"
      }
    >
      <InlineAlert>
        실제 서버의 내 프로필을 읽습니다. 먼저 로그인하거나 인증 예제에서 기존 세션을 확인하세요.
        아래 오류 예시는 실제 서버 응답과 구분됩니다.
      </InlineAlert>
      <p>
        로그인: {sessionStatusLabels[session.status]} · 조회:{' '}
        {query.fetchStatus === 'fetching'
          ? '불러오는 중'
          : query.fetchStatus === 'paused'
            ? '대기 중'
            : '요청 없음'}{' '}
        · 캐시:{' '}
        {query.data
          ? query.isStale || cacheInvalidated
            ? '있음 · 다시 조회 필요'
            : '있음 · 최신'
          : '없음'}
      </p>
      <Button
        disabled={session.status !== 'authenticated' || query.isFetching}
        onClick={() => void query.refetch()}
      >
        실제 내 프로필 조회
      </Button>
      <Button
        disabled={!query.isFetching}
        onClick={() => void queryClient.cancelQueries({ queryKey: key, exact: true })}
      >
        진행 조회 취소
      </Button>
      <Button
        disabled={!query.data}
        onClick={() =>
          void queryClient.invalidateQueries({ queryKey: key, exact: true, refetchType: 'none' })
        }
      >
        캐시 무효화 · 자동 재조회 없음
      </Button>
      {query.data && <p>내 이름: {query.data.data.displayName}</p>}
      {query.error && (
        <InlineAlert tone="error">
          {query.error.message}
          {query.error.traceId && ` · 진단 ID ${query.error.traceId}`}
        </InlineAlert>
      )}
      <Button onClick={() => setError(normalizeApiError(new Error('SYNTHETIC_NONPUBLIC_ERROR')))}>
        알 수 없는 오류 재현
      </Button>
      <Button
        onClick={() =>
          setError(
            new ApiError('VALIDATION_FAILED', 400, '550e8400-e29b-41d4-a716-446655440000', [
              { field: 'name', code: 'REQUIRED' },
            ]),
          )
        }
      >
        입력 오류 재현
      </Button>
      {error && (
        <InlineAlert tone="error">
          {error.message}
          {error.traceId && ` · 진단 ID ${error.traceId}`}
        </InlineAlert>
      )}
      <Link href="/users">실제 사용자 조회 연결 위치</Link>
    </ExampleSection>
  );
}

/** 인증 상태와 수동 조작을 실제 공통 경계에 연결하되 자동 폴링은 시작하지 않는다. */
export function AuthExample() {
  const session = useSession();
  const [message, setMessage] = useState('');

  return (
    <ExampleSection
      title="로그인 상태와 유지 확인"
      description="실제 인증 조작입니다. JWT/refresh는 HttpOnly 쿠키만 사용하며 UI가 해독하거나 저장하지 않습니다. 여러 탭의 동작과 동시 로그인 정책은 연결된 서버 설정을 따릅니다."
      code={
        "const session = useSession();\nconst allowed = usePermission('USER_READ');\n// 상태 checking/authenticated/anonymous/error는 구분됩니다."
      }
    >
      <p>로그인 상태: {sessionStatusLabels[session.status]}</p>
      {session.error && <InlineAlert tone="error">{session.error.message}</InlineAlert>}
      <ClientOnly>
        <p>
          여러 탭의 갱신 조정:{' '}
          {typeof navigator !== 'undefined' && navigator.locks
            ? '지원'
            : '지원하지 않음 · 다시 로그인 필요'}
        </p>
      </ClientOnly>
      <Button onClick={() => void session.bootstrap(true)}>인증 확인</Button>
      <Button
        disabled={session.status !== 'authenticated'}
        onClick={() => {
          void recoverSession()
            .then(() =>
              setMessage('로그인 유효성을 확인했습니다. 만료된 경우 서버에 갱신을 요청합니다.'),
            )
            .catch((error: unknown) => setMessage(normalizeApiError(error).message));
        }}
      >
        로그인 유지 확인
      </Button>
      <p role="status">{message}</p>
      <Link href="/login">로그인</Link>
      <Link href="/me">내 정보·비밀번호·로그아웃</Link>
    </ExampleSection>
  );
}
