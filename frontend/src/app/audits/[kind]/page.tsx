import { notFound } from 'next/navigation';

import { AuditScreen } from '@/features/data/AuditScreen';

/** 이력 종류별 권한과 UTC 기간 입력을 분리한다. */
export default async function AuditPage({ params }: { params: Promise<{ kind: string }> }) {
  const { kind } = await params;

  if (kind !== 'access' && kind !== 'security') {
    notFound();
  }

  return <AuditScreen key={kind} kind={kind} />;
}
