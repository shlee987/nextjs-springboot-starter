import { notFound } from 'next/navigation';
import type { ReactNode } from 'react';

/** 예제 비활성 빌드는 메뉴뿐 아니라 직접 URL도 서버에서 404로 차단한다. */
export default function ExamplesLayout({ children }: { children: ReactNode }) {
  if (process.env.APP_EXAMPLES_ENABLED !== 'true') {
    notFound();
  }

  return <>{children}</>;
}
