export type ActionResult<T> =
  { status: 'completed'; value: T } | { status: 'ignored' | 'cancelled' };

/** 화면/사용자 범위의 작업 잠금이다. 취소 요청만으로 풀지 않고 실제 Promise 종료까지 같은 키를 막는다. */
export function createActionScope(isCurrent: () => boolean = () => true) {
  const running = new Map<string, AbortController>();
  const listeners = new Set<() => void>();
  let disposed = false;
  let leases = 0;
  let detached = false;

  /** 인증 세대가 이미 바뀌었으면 React 재렌더 전에도 이전 작업의 실행·성공 반영을 막는다. */
  function isActive() {
    return !disposed && !detached && isCurrent();
  }

  /** 구독자가 작업별 snapshot을 다시 읽게 한다. 값·오류·자격 정보는 저장하지 않는다. */
  function emit() {
    listeners.forEach((listener) => listener());
  }

  /** 동기 잠금 뒤 한 번 실행한다. 실패는 원래 오류로 reject하고 중복은 실행 없이 결과로 돌려준다. */
  async function run<T>(
    key: string,
    task: (signal: AbortSignal) => T | Promise<T>,
  ): Promise<ActionResult<T>> {
    if (!isActive()) return { status: 'cancelled' };
    if (running.has(key)) return { status: 'ignored' };

    const controller = new AbortController();
    running.set(key, controller);
    emit();

    try {
      const value = await task(controller.signal);

      return !isActive() || controller.signal.aborted
        ? { status: 'cancelled' }
        : { status: 'completed', value };
    } catch (failure) {
      if (!isActive() || controller.signal.aborted) return { status: 'cancelled' };

      throw failure;
    } finally {
      if (running.get(key) === controller) {
        running.delete(key);
        emit();
      }
    }
  }

  /** 범위가 끝나면 취소 신호를 보낸다. 취소를 무시한 늦은 완료도 completed로 전달하지 않는다. */
  function dispose() {
    disposed = true;
    running.forEach((controller) => controller.abort());
  }

  return {
    run,
    isActive,
    dispose,
    isRunning: (key: string) => running.has(key),
    cancel: (key: string) => running.get(key)?.abort(),
    subscribe: (listener: () => void) => {
      listeners.add(listener);

      return () => {
        listeners.delete(listener);
      };
    },
    /** StrictMode의 즉시 해제/재연결을 실제 화면 종료와 구분한다. */
    retain: () => {
      leases += 1;
      detached = false;

      return () => {
        leases -= 1;
        if (leases === 0) detached = true;
        queueMicrotask(() => {
          if (leases === 0) dispose();
        });
      };
    },
  };
}
