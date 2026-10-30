import type { Metadata } from 'next';
import { headers } from 'next/headers';
import type { ReactNode } from 'react';

import { AppProviders } from '@/providers/AppProviders';
import { ScreenActionScope } from '@/providers/ScreenActionScope';
import { AppShell } from '@/components/layout/AppShell';

import './globals.css';

export const metadata: Metadata = { title: 'Starter', description: '프로젝트 기본 구성' };
export const dynamic = 'force-dynamic';

/** 요청별 nonce 문맥을 유지하고 브라우저 상태를 문서마다 분리한다. */
export default async function RootLayout({ children }: { children: ReactNode }) {
  const nonce = (await headers()).get('x-nonce') ?? undefined;

  return (
    <html lang="ko">
      <body>
        <AppProviders nonce={nonce}>
          <ScreenActionScope>
            <AppShell>{children}</AppShell>
          </ScreenActionScope>
        </AppProviders>
      </body>
    </html>
  );
}
