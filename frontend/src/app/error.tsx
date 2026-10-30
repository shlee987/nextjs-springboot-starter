'use client';

import { messages } from '@/messages/messages';
import { Button } from '@/components/ui/Button';

/** 렌더링 실패의 원문을 표시하지 않고 명시적인 재시도만 제공한다. */
export default function ErrorPage({ reset }: { reset: () => void }) {
  return (
    <section>
      <h1>{messages.UNEXPECTED_ERROR}</h1>
      <Button onClick={reset}>다시 시도</Button>
    </section>
  );
}
