/** 서버를 호출하지 않는 예제 지연이며 취소 신호가 오면 타이머와 대기를 함께 끝낸다. */
export function exampleDelay(milliseconds: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    signal.throwIfAborted();
    const timer = setTimeout(() => {
      signal.removeEventListener('abort', cancel);
      resolve();
    }, milliseconds);

    function cancel() {
      clearTimeout(timer);
      reject(signal.reason);
    }

    signal.addEventListener('abort', cancel, { once: true });
  });
}
