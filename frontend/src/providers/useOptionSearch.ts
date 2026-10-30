'use client';

import { useEffect, useRef, useState } from 'react';
import type { Option, SearchControl } from '@/components/ui/Selections';

/** 제어된 검색의 입력 대기·취소·최신 응답만 반영한다. 검색 함수는 호출자가 제공하며 실패 원문은 표시하지 않는다. */
export function useOptionSearch(
  search: (query: string, signal: AbortSignal) => Promise<Option[]>,
  { minLength = 2, delayMs = 250 }: { minLength?: number; delayMs?: number } = {},
) {
  const [query, setInput] = useState('');
  const [retry, setRetry] = useState(0);
  const [result, setResult] = useState<{ options: Option[]; status: SearchControl['status'] }>({
    options: [],
    status: 'idle',
  });
  const version = useRef(0);
  const controller = useRef<AbortController | undefined>(undefined);

  useEffect(() => {
    if (query.length < minLength) return;

    const current = ++version.current;
    const request = new AbortController();
    controller.current = request;
    let active = true;
    const timer = setTimeout(async () => {
      setResult({ options: [], status: 'loading' });

      try {
        const options = await search(query, request.signal);

        if (active && !request.signal.aborted && current === version.current)
          setResult({ options, status: 'success' });
      } catch {
        if (active && !request.signal.aborted && current === version.current)
          setResult({ options: [], status: 'error' });
      }
    }, delayMs);

    return () => {
      active = false;
      clearTimeout(timer);
      request.abort();

      if (controller.current === request) controller.current = undefined;
    };
  }, [query, retry, minLength, delayMs, search]);

  /** React 재렌더 전에 이전 응답을 무효화하고 입력 대기 중에도 과거 선택지를 감춘다. */
  function onQueryChange(next: string) {
    version.current += 1;
    controller.current?.abort();
    setInput(next);
    setResult({ options: [], status: next.length < minLength ? 'idle' : 'loading' });
  }

  return {
    options: result.options,
    search: {
      query,
      onQueryChange,
      minLength,
      status: query.length < minLength ? ('idle' as const) : result.status,
      onRetry: () => {
        version.current += 1;
        setResult({ options: [], status: 'loading' });
        setRetry((count) => count + 1);
      },
    } satisfies SearchControl,
  };
}
