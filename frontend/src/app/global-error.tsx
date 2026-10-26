'use client';

import { messages } from '@/messages/messages';

/** 루트 레이아웃 실패에도 진단 정보가 없는 독립 문서를 제공한다. */
export default function GlobalError({ reset }: { reset: () => void }) {
  return (
    <html lang="ko">
      <body>
        <main>
          <h1>{messages.UNEXPECTED_ERROR}</h1>
          <button onClick={reset}>다시 시도</button>
        </main>
      </body>
    </html>
  );
}
