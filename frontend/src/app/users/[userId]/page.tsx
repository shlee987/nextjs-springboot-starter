import { notFound } from 'next/navigation';

import { UserDetailScreen } from '@/features/data/ProfilePanel';

/** 개인 상세는 PERSONAL_INFO_READ 권한을 별도로 확인한다. */
export default async function UserPage({ params }: { params: Promise<{ userId: string }> }) {
  const { userId } = await params;

  if (!/^[1-9][0-9]{0,18}$/.test(userId) || BigInt(userId) > 9223372036854775807n) {
    notFound();
  }

  return <UserDetailScreen userId={userId} />;
}
